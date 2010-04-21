// Copyright (C) 2009 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.sshd.commands;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.PrintWriter;
import java.util.Collections;

/** Create a new project. **/
@AdminCommand
final class AdminCreateProject extends BaseCommand {
  @Option(name = "--name", required = true, aliases = {"-n"}, metaVar = "NAME", usage = "name of project to be created")
  private String projectName;

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner of project\n"
      + "(default: Administrators)")
  private AccountGroup.Id ownerId;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESC", usage = "description of project")
  private String projectDescription = "";

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType = SubmitType.MERGE_IF_NECESSARY;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  private boolean contributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  private boolean signedOffBy;

  @Option(name = "--branch", aliases = {"-b"}, metaVar = "BRANCH", usage = "initial branch name\n"
      + "(default: master)")
  private String branch = Constants.MASTER;

  @Inject
  private ReviewDb db;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private AuthConfig authConfig;

  @Inject
  private ReplicationQueue rq;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(out);

        ownerId = authConfig.getAdministratorsGroup();
        parseCommandLine();

        try {
          validateParameters();

          Repository repo = repoManager.createRepository(projectName);
          repo.create(true);

          RefUpdate u = repo.updateRef(Constants.HEAD);
          u.disableRefLog();
          u.link(branch);

          repoManager.setProjectDescription(projectName, projectDescription);

          createProject();

          rq.replicateNewProject(new Project.NameKey(projectName), branch);
        } catch (Exception e) {
          p.print("Error when trying to create project: " + e.getMessage()
              + "\n");
          p.flush();
        }

      }
    });
  }

  private void createProject() throws OrmException {
    final Project.NameKey newProjectNameKey = new Project.NameKey(projectName);

    final RefRight.Key prk =
        new RefRight.Key(newProjectNameKey, new RefRight.RefPattern(
            RefRight.ALL), ApprovalCategory.OWN, ownerId);
    final RefRight pr = new RefRight(prk);
    pr.setMaxValue((short) 1);
    pr.setMinValue((short) 1);
    db.refRights().insert(Collections.singleton(pr));

    final Project newProject = new Project(newProjectNameKey);
    newProject.setDescription(projectDescription);
    newProject.setSubmitType(submitType);
    newProject.setUseContributorAgreements(contributorAgreements);
    newProject.setUseSignedOffBy(signedOffBy);

    db.projects().insert(Collections.singleton(newProject));
  }

  private void validateParameters() throws Failure {
    if (projectName.endsWith(".git")) {
      projectName =
          projectName.substring(0, projectName.length() - ".git".length());
    }

    while (branch.startsWith("/")) {
      branch = branch.substring(1);
    }
    if (!branch.startsWith(Constants.R_HEADS)) {
      branch = Constants.R_HEADS + branch;
    }
    if (!Repository.isValidRefName(branch)) {
      throw new Failure(1, "--branch \"" + branch + "\" is not a valid name");
    }
  }
}
