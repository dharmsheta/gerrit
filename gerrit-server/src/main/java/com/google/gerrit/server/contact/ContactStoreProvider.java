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

package com.google.gerrit.server.contact;

import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.SitePaths;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.util.StringUtils;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.Security;

/** Creates the {@link ContactStore} based on the configuration. */
public class ContactStoreProvider implements Provider<ContactStore> {
  private final Config config;
  private final SitePaths site;
  private final SchemaFactory<ReviewDb> schema;
  private final ContactStoreConnection.Factory connFactory;

  @Inject
  ContactStoreProvider(@GerritServerConfig final Config config,
      final SitePaths site, final SchemaFactory<ReviewDb> schema,
      final ContactStoreConnection.Factory connFactory) {
    this.config = config;
    this.site = site;
    this.schema = schema;
    this.connFactory = connFactory;
  }

  @Override
  public ContactStore get() {
    final String url = config.getString("contactstore", null, "url");
    if (StringUtils.isEmptyOrNull(url)) {
      return new NoContactStore();
    }

    if (!havePGP()) {
      throw new ProvisionException("BouncyCastle PGP not installed; "
          + " needed to encrypt contact information");
    }

    final URL storeUrl;
    try {
      storeUrl = new URL(url);
    } catch (MalformedURLException e) {
      throw new ProvisionException("Invalid contactstore.url: " + url, e);
    }

    final String storeAPPSEC = config.getString("contactstore", null, "appsec");
    final File pubkey = site.contact_information_pub;
    if (!pubkey.exists()) {
      throw new ProvisionException("PGP public key file \""
          + pubkey.getAbsolutePath() + "\" not found");
    }
    return new EncryptedContactStore(storeUrl, storeAPPSEC, pubkey, schema,
        connFactory);
  }

  private static boolean havePGP() {
    try {
      Class.forName(PGPPublicKey.class.getName());
      addBouncyCastleProvider();
      return true;
    } catch (NoClassDefFoundError noBouncyCastle) {
      return false;
    } catch (ClassNotFoundException noBouncyCastle) {
      return false;
    } catch (SecurityException noBouncyCastle) {
      return false;
    } catch (NoSuchMethodException noBouncyCastle) {
      return false;
    } catch (InstantiationException noBouncyCastle) {
      return false;
    } catch (IllegalAccessException noBouncyCastle) {
      return false;
    } catch (InvocationTargetException noBouncyCastle) {
      return false;
    } catch (ClassCastException noBouncyCastle) {
      return false;
    }
  }

  private static void addBouncyCastleProvider() throws ClassNotFoundException,
          SecurityException, NoSuchMethodException, InstantiationException,
          IllegalAccessException, InvocationTargetException {
    Class<?> clazz = Class.forName(BouncyCastleProvider.class.getName());
    Constructor<?> constructor = clazz.getConstructor();
    Security.addProvider((java.security.Provider) constructor.newInstance());
  }
}
