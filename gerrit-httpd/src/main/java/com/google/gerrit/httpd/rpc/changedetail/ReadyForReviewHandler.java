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


package com.google.gerrit.httpd.rpc.changedetail;

import com.google.gerrit.common.data.ChangeDetail;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.common.errors.NoSuchEntityException;
import com.google.gerrit.httpd.rpc.Handler;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.changedetail.ReadyForReview;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import javax.annotation.Nullable;

public class ReadyForReviewHandler extends Handler<ChangeDetail> {

  interface Factory {
    ReadyForReviewHandler create(PatchSet.Id patchSetId, String message);
  }

  private final ReadyForReview.Factory readyForReviewFactory;
  private final ChangeDetailFactory.Factory changeDetailFactory;

  private final PatchSet.Id patchSetId;
  @Nullable
  private final String message;

  @Inject
  ReadyForReviewHandler(final ReadyForReview.Factory readyForReviewFactory,
      final ChangeDetailFactory.Factory changeDetailFactory,
      @Assisted final PatchSet.Id patchSetId,
      @Assisted @Nullable final String message) {
    this.readyForReviewFactory = readyForReviewFactory;
    this.changeDetailFactory = changeDetailFactory;

    this.patchSetId = patchSetId;
    this.message = message;
  }

  @Override
  public ChangeDetail call() throws NoSuchChangeException, OrmException,
      EmailException, NoSuchEntityException, InvalidChangeOperationException,
      PatchSetInfoNotAvailableException {
    final ReviewResult result =
        readyForReviewFactory.create(patchSetId, message).call();
    if (result.getErrors().size() > 0) {
      throw new NoSuchChangeException(result.getChangeId());
    }
    return changeDetailFactory.create(result.getChangeId()).call();
  }

}
