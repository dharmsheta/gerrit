// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License.package com.google.gerrit.server.git;

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.cache.Cache;
import com.google.gerrit.server.cache.CacheModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.eclipse.jgit.lib.ObjectId;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

@Singleton
public class TagCache {
  private static final String CACHE_NAME = "git_tags";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        final TypeLiteral<Cache<EntryKey, EntryVal>> type =
            new TypeLiteral<Cache<EntryKey, EntryVal>>() {};
        disk(type, CACHE_NAME);
        bind(TagCache.class);
      }
    };
  }

  private final Cache<EntryKey, EntryVal> cache;
  private final Object createLock = new Object();

  @Inject
  TagCache(@Named(CACHE_NAME) Cache<EntryKey, EntryVal> cache) {
    this.cache = cache;
  }

  /**
   * Advise the cache that a reference fast-forwarded.
   * <p>
   * This operation is not necessary, the cache will automatically detect changes
   * made to references and update itself on demand. However, this method may
   * allow the cache to update more quickly and reuse the caller's computation of
   * the fast-forward status of a branch.
   *
   * @param name project the branch is contained in.
   * @param refName the branch name.
   * @param oldValue the old value, before the fast-forward. The cache
   *        will only update itself if it is still using this old value.
   * @param newValue the current value, after the fast-forward.
   */
  public void updateFastForward(Project.NameKey name, String refName,
      ObjectId oldValue, ObjectId newValue) {
    // Be really paranoid and null check everything. This method should
    // never fail with an exception. Some of these references can be null
    // (e.g. not all projects are cached, or the cache is not current).
    //
    EntryVal val = cache.get(new EntryKey(name));
    if (val != null) {
      TagSetHolder holder = val.holder;
      if (holder != null) {
        TagSet tags = holder.getTagSet();
        if (tags != null) {
          tags.updateFastForward(refName, oldValue, newValue);
        }
      }
    }
  }

  TagSetHolder get(Project.NameKey name) {
    EntryKey key = new EntryKey(name);
    EntryVal val = cache.get(key);
    if (val == null) {
      synchronized (createLock) {
        val = cache.get(key);
        if (val == null) {
          val = new EntryVal();
          val.holder = new TagSetHolder(name);
          cache.put(key, val);
        }
      }
    }
    return val.holder;
  }

  static class EntryKey implements Serializable {
    static final long serialVersionUID = 1L;

    private transient String name;

    EntryKey(Project.NameKey name) {
      this.name = name.get();
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof EntryKey) {
        return name.equals(((EntryKey) o).name);
      }
      return false;
    }

    private void readObject(ObjectInputStream in) throws IOException {
      name = in.readUTF();
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      out.writeUTF(name);
    }
  }

  static class EntryVal implements Serializable {
    static final long serialVersionUID = EntryKey.serialVersionUID;

    transient TagSetHolder holder;

    private void readObject(ObjectInputStream in) throws IOException,
        ClassNotFoundException {
      holder = new TagSetHolder(new Project.NameKey(in.readUTF()));
      if (in.readBoolean()) {
        TagSet tags = new TagSet(holder.getProjectName());
        tags.readObject(in);
        holder.setTagSet(tags);
      }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
      TagSet tags = holder.getTagSet();
      out.writeUTF(holder.getProjectName().get());
      out.writeBoolean(tags != null);
      if (tags != null) {
        tags.writeObject(out);
      }
    }
  }
}
