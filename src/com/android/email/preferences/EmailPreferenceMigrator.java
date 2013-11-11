/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.email.preferences;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.android.email.Preferences;
import com.android.emailcommon.provider.EmailContent;
import com.android.emailcommon.provider.EmailContent.AccountColumns;
import com.android.mail.preferences.BasePreferenceMigrator;
import com.android.mail.preferences.FolderPreferences;
import com.android.mail.preferences.MailPrefs;
import com.android.mail.providers.Account;
import com.android.mail.providers.Folder;
import com.android.mail.providers.UIProvider;
import com.android.mail.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Migrates Email settings to UnifiedEmail
 */
public class EmailPreferenceMigrator extends BasePreferenceMigrator {
    private static final String LOG_TAG = "EmailPrefMigrator";

    @Override
    protected void migrate(final Context context, final int oldVersion, final int newVersion) {
        final List<Account> accounts = new ArrayList<Account>();

        final Cursor accountCursor = context.getContentResolver().query(Uri.parse(
                EmailContent.CONTENT_URI + "/uiaccts"),
                UIProvider.ACCOUNTS_PROJECTION_NO_CAPABILITIES, null, null, null);

        if (accountCursor == null) {
            LogUtils.wtf(LOG_TAG,
                    "Null cursor returned from query to %s when migrating accounts from %d to %d",
                    EmailContent.CONTENT_URI + "/uiaccts",
                    oldVersion, newVersion);
        } else {
            try {
                while (accountCursor.moveToNext()) {
                    accounts.add(new Account(accountCursor));
                }
            } finally {
                accountCursor.close();
            }
        }

        migrate(context, oldVersion, newVersion, accounts);
    }

    protected static void migrate(final Context context, final int oldVersion, final int newVersion,
            final List<Account> accounts) {
        final Preferences preferences = Preferences.getPreferences(context);
        final MailPrefs mailPrefs = MailPrefs.get(context);
        if (oldVersion < 1) {
            // Move global settings

            @SuppressWarnings("deprecation")
            final boolean hasSwipeDelete = preferences.hasSwipeDelete();
            if (hasSwipeDelete) {
                @SuppressWarnings("deprecation")
                final boolean swipeDelete = preferences.getSwipeDelete();
                mailPrefs.setConversationListSwipeEnabled(swipeDelete);
            }

            // Move reply-all setting
            @SuppressWarnings("deprecation")
            final boolean isReplyAllSet = preferences.hasReplyAll();
            if (isReplyAllSet) {
                @SuppressWarnings("deprecation")
                final boolean replyAll = preferences.getReplyAll();
                mailPrefs.setDefaultReplyAll(replyAll);
            }

            // Move folder notification settings
            for (final Account account : accounts) {
                // Get the emailcommon account
                final Cursor ecAccountCursor = context.getContentResolver().query(
                        com.android.emailcommon.provider.Account.CONTENT_URI,
                        com.android.emailcommon.provider.Account.CONTENT_PROJECTION,
                        AccountColumns.EMAIL_ADDRESS + " = ?", new String[] { account.name },
                        null);
                final com.android.emailcommon.provider.Account ecAccount =
                        new com.android.emailcommon.provider.Account();


                if (ecAccountCursor == null) {
                    LogUtils.e(LOG_TAG, "Null old account cursor for mailbox %s",
                            LogUtils.sanitizeName(LOG_TAG, account.name));
                    continue;
                }

                try {
                    if (ecAccountCursor.moveToFirst()) {
                        ecAccount.restore(ecAccountCursor);
                    } else {
                        LogUtils.e(LOG_TAG, "Couldn't load old account for mailbox %s",
                                LogUtils.sanitizeName(LOG_TAG, account.name));
                        continue;
                    }
                } finally {
                    ecAccountCursor.close();
                }

                // The only setting in AccountPreferences so far is a global notification toggle,
                // but we only allow Inbox notifications, so it will remain unused
                final Cursor folderCursor =
                        context.getContentResolver().query(account.settings.defaultInbox,
                                UIProvider.FOLDERS_PROJECTION, null, null, null);

                if (folderCursor == null) {
                    LogUtils.e(LOG_TAG, "Null folder cursor for mailbox %s",
                            LogUtils.sanitizeName(LOG_TAG,
                                    account.settings.defaultInbox.toString()));
                    continue;
                }

                Folder folder = null;
                try {
                    if (folderCursor.moveToFirst()) {
                        folder = new Folder(folderCursor);
                    }
                } finally {
                    folderCursor.close();
                }

                final FolderPreferences folderPreferences =
                        new FolderPreferences(context, account.getEmailAddress(), folder,
                                true /* inbox */);

                @SuppressWarnings("deprecation")
                final boolean notify = (ecAccount.getFlags()
                        & com.android.emailcommon.provider.Account.FLAGS_NOTIFY_NEW_MAIL) != 0;
                folderPreferences.setNotificationsEnabled(notify);

                @SuppressWarnings("deprecation")
                final String ringtoneUri = ecAccount.getRingtone();
                folderPreferences.setNotificationRingtoneUri(ringtoneUri);

                @SuppressWarnings("deprecation")
                final boolean vibrate = (ecAccount.getFlags()
                        & com.android.emailcommon.provider.Account.FLAGS_VIBRATE) != 0;
                folderPreferences.setNotificationVibrateEnabled(vibrate);

                folderPreferences.commit();
            }
        }

        if (oldVersion < 2) {
            @SuppressWarnings("deprecation")
            final Set<String> whitelistedAddresses = preferences.getWhitelistedSenderAddresses();
            mailPrefs.setSenderWhitelist(whitelistedAddresses);
        }

        if (oldVersion < 3) {
            @SuppressWarnings("deprecation")
            // The default for the conversation list icon is the sender image.
            final boolean showSenderImages = !TextUtils.equals(
                    Preferences.CONV_LIST_ICON_NONE, preferences.getConversationListIcon());
            mailPrefs.setShowSenderImages(showSenderImages);
        }
    }
}
