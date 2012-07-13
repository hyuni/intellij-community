/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xml.index;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class XmlNamespaceIndex extends XmlIndex<XsdNamespaceBuilder> {

  @Nullable
  public static String getNamespace(@NotNull VirtualFile file, final Project project) {
    final List<XsdNamespaceBuilder> list = FileBasedIndex.getInstance().getValues(NAME, file.getUrl(), createFilter(project));
    return list.size() == 0 ? null : list.get(0).getNamespace();
  }

  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getResourcesByNamespace(String namespace, final Project project, Module module) {
    List<IndexedRelevantResource<String, XsdNamespaceBuilder>> resources = IndexedRelevantResource.getResources(NAME, namespace, module, project,
                                                                                                   null);
    Collections.sort(resources);
    return resources;
  }

  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getAllResources(@NotNull final Module module) {
    return getAllResources(module, null);
  }

  public static List<IndexedRelevantResource<String, XsdNamespaceBuilder>> getAllResources(@NotNull final Module module,
                                                                                           @Nullable NullableFunction<List<IndexedRelevantResource<String, XsdNamespaceBuilder>>, IndexedRelevantResource<String, XsdNamespaceBuilder>> chooser) {
    return IndexedRelevantResource.getAllResources(NAME, module, chooser);
  }
  
  private static final ID<String,XsdNamespaceBuilder> NAME = ID.create("XmlNamespaces");

  @Override
  @NotNull
  public ID<String, XsdNamespaceBuilder> getName() {
    return NAME;
  }

  @Override
  @NotNull
  public DataIndexer<String, XsdNamespaceBuilder, FileContent> getIndexer() {
    return new DataIndexer<String, XsdNamespaceBuilder, FileContent>() {
      @Override
      @NotNull
      public Map<String, XsdNamespaceBuilder> map(final FileContent inputData) {
        final XsdNamespaceBuilder
          builder = XsdNamespaceBuilder.computeNamespace(CharArrayUtil.readerFromCharSequence(inputData.getContentAsText()));
        final HashMap<String, XsdNamespaceBuilder> map = new HashMap<String, XsdNamespaceBuilder>(2);
        String namespace = builder.getNamespace();
        if (namespace != null) {
          map.put(namespace, builder);
        }
        map.put(inputData.getFile().getUrl(), builder);
        return map;
      }
    };
  }

  @Override
  public DataExternalizer<XsdNamespaceBuilder> getValueExternalizer() {
    return new DataExternalizer<XsdNamespaceBuilder>() {
      @Override
      public void save(DataOutput out, XsdNamespaceBuilder value) throws IOException {
        out.writeUTF(value.getNamespace() == null ? "" : value.getNamespace());
        out.writeUTF(value.getVersion() == null ? "" : value.getVersion());
        out.writeInt(value.getTags().size());
        for (String s : value.getTags()) {
          out.writeUTF(s);
        }
      }

      @Override
      public XsdNamespaceBuilder read(DataInput in) throws IOException {

        int count;
        XsdNamespaceBuilder builder = new XsdNamespaceBuilder(in.readUTF(), in.readUTF(), new ArrayList<String>(count = in.readInt()));
        for (int i = 0; i < count; i++) {
          builder.getTags().add(in.readUTF());
        }
        return builder;
      }
    };
  }

  @Override
  public int getVersion() {
    return 2;
  }

  @Nullable
  public static IndexedRelevantResource<String, XsdNamespaceBuilder> guessSchema(String namespace,
                                                                                 @Nullable final String tagName,
                                                                                 @Nullable final String version,
                                                                                 Module module) {

    if (module == null) return null;

    Project project = module.getProject();
    final List<IndexedRelevantResource<String, XsdNamespaceBuilder>>
      resources = getResourcesByNamespace(namespace, project, module);

    if (resources.isEmpty()) return null;

    return Collections
      .max(resources, new Comparator<IndexedRelevantResource<String, XsdNamespaceBuilder>>() {
        @Override
        public int compare(IndexedRelevantResource<String, XsdNamespaceBuilder> o1,
                           IndexedRelevantResource<String, XsdNamespaceBuilder> o2) {

          int i = o1.getValue().getRating(tagName, version) - o2.getValue().getRating(tagName, version);
          return i == 0 ? o1.compareTo(o2) : i;
        }
      });
  }
}
