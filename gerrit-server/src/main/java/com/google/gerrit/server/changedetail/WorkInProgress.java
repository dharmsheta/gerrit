// Copyright (C) 2012 The Android Open Source Project
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


package com.google.gerrit.server.changedetail;

import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.WorkInProgressSender;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.concurrent.Callable;

public class WorkInProgress implements Callable<ReviewResult> {

  public interface Factory {
    WorkInProgress create(PatchSet.Id patchSetId, String changeComment);
  }

  private final WorkInProgressSender.Factory wipSenderFactory;
  private final ChangeControl.Factory changeControlFactory;
  private final ReviewDb db;
  private final IdentifiedUser currentUser;

  private final PatchSet.Id patchSetId;
  private final String changeComment;

  @Inject
  WorkInProgress(final WorkInProgressSender.Factory wipSenderFactory,
      final ChangeControl.Factory changeControlFactory, final ReviewDb db,
      final IdentifiedUser currentUser, final ChangeHooks hooks,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted final String changeComment) {
    this.wipSenderFactory = wipSenderFactory;
    this.changeControlFactory = changeControlFactory;
    this.db = db;
    this.currentUser = currentUser;

    this.patchSetId = patchSetId;
    this.changeComment = changeComment;
  }

  @Override
  public ReviewResult call() throws EmailException,
      InvalidChangeOperationException, NoSuchChangeException, OrmException {
    final ReviewResult result = new ReviewResult();

    final Change.Id changeId = patchSetId.getParentKey();
    result.setChangeId(changeId);
    final ChangeControl control = changeControlFactory.validateFor(changeId);
    final PatchSet patch = db.patchSets().get(patchSetId);
    if (!control.canSetWorkInProgress()) {
      result.addError(new ReviewResult.Error(
          ReviewResult.Error.Type.WORKINPROGRESS_NOT_PERMITTED));
    } else if (patch == null) {
      throw new NoSuchChangeException(changeId);
    } else {

      // Create a message to accompany the abandoned change
      final ChangeMessage cmsg = new ChangeMessage(
          new ChangeMessage.Key(changeId, ChangeUtil.messageUUID(db)),
          currentUser.getAccountId(), patchSetId);
      final StringBuilder msgBuf =
          new StringBuilder("Patch Set " + patchSetId.get() + ": Work In Progress");
      if (changeComment != null && changeComment.length() > 0) {
        msgBuf.append("\n\n");
        msgBuf.append(changeComment);
      }
      cmsg.setMessage(msgBuf.toString());

      // Set the status to Work In Progress
      final Change updatedChange = db.changes().atomicUpdate(changeId,
          new AtomicUpdate<Change>() {
        @Override
        public Change update(Change change) {
          if (change.getStatus().isOpen()
              && change.currentPatchSetId().equals(patchSetId)
              && (change.getStatus() != Change.Status.WORKINPROGRESS)) {
            change.setStatus(Change.Status.WORKINPROGRESS);
            ChangeUtil.updated(change);
            return change;
          } else {
            return null;
          }
        }
      });
      ChangeUtil.updatedChange(
          db, currentUser, updatedChange, cmsg, wipSenderFactory,
          "Change is no longer open or patchset is not latest");
    }

    return result;
  }
}
