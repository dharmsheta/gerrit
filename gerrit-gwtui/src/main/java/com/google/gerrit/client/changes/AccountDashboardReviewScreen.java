// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.client.changes;

import com.google.gerrit.client.FormatUtil;
import com.google.gerrit.client.Gerrit;
import com.google.gerrit.client.changes.ChangeTable.ApprovalViewType;
import com.google.gerrit.client.rpc.ScreenLoadCallback;
import com.google.gerrit.client.ui.Screen;
import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.AccountDashboardReviewInfo;
import com.google.gerrit.common.data.AccountInfo;
import com.google.gerrit.reviewdb.client.Account;


public class AccountDashboardReviewScreen extends Screen implements ChangeListScreen {
  private final Account.Id ownerId;
  private ChangeTable table;
  private ChangeTable.Section byOwner;
  private ChangeTable.Section forReview;
  private ChangeTable.Section haveReviewed;

  public AccountDashboardReviewScreen(final Account.Id id) {
    ownerId = id;
  }

  @Override
  protected void onInitUI() {
    super.onInitUI();
    table = new ChangeTable(true);
    table.addStyleName(Gerrit.RESOURCES.css().accountDashboard());
    byOwner = new ChangeTable.Section("", ApprovalViewType.STRONGEST, null);
    forReview = new ChangeTable.Section("", ApprovalViewType.STRONGEST, null);
    haveReviewed = new ChangeTable.Section("", ApprovalViewType.STRONGEST, null);

    table.addSection(byOwner);
    table.addSection(forReview);
    table.addSection(haveReviewed);
    add(table);
    table.setSavePointerId(PageLinks.toAccountDashboard(ownerId));
  }

  @Override
  protected void onLoad() {
    super.onLoad();
    Util.LIST_SVC.reviewForAccount(ownerId,
        new ScreenLoadCallback<AccountDashboardReviewInfo>(this) {
          @Override
          protected void preDisplay(final AccountDashboardReviewInfo r) {
            display(r);
          }
        });
  }

  @Override
  public void registerKeys() {
    super.registerKeys();
    table.setRegisterKeys(true);
  }

  private void display(final AccountDashboardReviewInfo r) {
    table.setAccountInfoCache(r.getAccounts());

    final AccountInfo o = r.getAccounts().get(r.getOwner());
    final String name = FormatUtil.name(o);
    setWindowTitle(name);
    setPageTitle(Util.M.accountDashboardTitle(name));
    byOwner.setTitleText(Util.M.changesStartedBy(name));
    forReview.setTitleText(Util.M.changesToBeReviewedBy(name));
    haveReviewed.setTitleText(Util.M.changesReviewedBy(name));

    byOwner.display(r.getByOwner());
    forReview.display(r.getForReview());
    haveReviewed.display(r.getHaveReviewed());
    table.finishDisplay();
  }
}