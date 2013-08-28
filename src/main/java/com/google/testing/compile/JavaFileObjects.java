/*
 * Copyright (C) 2013 Google, Inc.
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
package com.google.testing.compile;

import static com.google.common.base.Preconditions.checkArgument;
import static javax.tools.JavaFileObject.Kind.SOURCE;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;

import javax.tools.ForwardingJavaFileObject;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;

/**
 * A utility class for creating {@link JavaFileObject} instances.
 *
 * @author Gregory Kick
 */
public final class JavaFileObjects {
  private JavaFileObjects() { }

  public static JavaFileObject forSourceString(String fullyQualifiedClassName,
      final String source) {
    return new StringSourceJavaFileObject(fullyQualifiedClassName, source);
  }

  private static final class StringSourceJavaFileObject extends SimpleJavaFileObject {
    final String source;
    final long lastModified;

    StringSourceJavaFileObject(String fullyQualifiedClassName, String source) {
      super(createUri(fullyQualifiedClassName), SOURCE);
      // TODO(gak): check that fullyQualifiedClassName looks like a fully qualified class name
      this.source = source;
      this.lastModified = System.currentTimeMillis();
    }

    static URI createUri(String fullyQualifiedClassName) {
      return URI.create(CharMatcher.is('.').replaceFrom(fullyQualifiedClassName, '/')
          + SOURCE.extension);
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }

    @Override
    public OutputStream openOutputStream() {
      throw new IllegalStateException();
    }

    @Override
    public InputStream openInputStream() {
      return new ByteArrayInputStream(source.getBytes(Charset.defaultCharset()));
    }

    @Override
    public Writer openWriter() {
      throw new IllegalStateException();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) {
      return new StringReader(source);
    }

    @Override
    public long getLastModified() {
      return lastModified;
    }
  }

  public static JavaFileObject forResource(URL resourceUrl) {
    if ("jar".equals(resourceUrl.getProtocol())) {
      return new JarFileJavaFileObject(resourceUrl);
    } else {
      return new ResourceSourceJavaFileObject(resourceUrl);
    }
  }

  public static JavaFileObject forResource(String resourceName) {
    return forResource(Resources.getResource(resourceName));
  }

  static Kind deduceKind(URI uri) {
    String path = uri.getPath();
    for (Kind kind : Kind.values()) {
      if (path.endsWith(kind.extension)) {
        return kind;
      }
    }
    return Kind.OTHER;
  }

  private static final class JarFileJavaFileObject
      extends ForwardingJavaFileObject<ResourceSourceJavaFileObject> {
    final URI jarFileUri;

    JarFileJavaFileObject(URL jarUrl) {
      // this is a cheap way to give SimpleJavaFileObject a uri that satisfies the contract
      // then we just override the methods that we want to behave differently for jars
      super(new ResourceSourceJavaFileObject(jarUrl, getPathUri(jarUrl)));
      this.jarFileUri = URI.create(jarUrl.toString());
    }

    static final Splitter jarUrlSplitter = Splitter.on('!');

    static final URI getPathUri(URL jarUrl) {
      ImmutableList<String> parts = ImmutableList.copyOf(jarUrlSplitter.split(jarUrl.getPath()));
      checkArgument(parts.size() == 2,
          "The jar url separator (!) appeared more than once in the url: %s", jarUrl);
      String pathPart = parts.get(1);
      checkArgument(!pathPart.endsWith("/"), "cannot create a java file object for a directory: %s",
          pathPart);
      return URI.create(pathPart);
    }

    @Override
    public String getName() {
      return jarFileUri.getSchemeSpecificPart();
    }
  }

  private static final class ResourceSourceJavaFileObject extends SimpleJavaFileObject {
    final ByteSource resourceByteSource;

    /** Only to avoid creating the URI twice. */
    ResourceSourceJavaFileObject(URL resourceUrl, URI resourceUri) {
      super(resourceUri, deduceKind(resourceUri));
      this.resourceByteSource = Resources.asByteSource(resourceUrl);
    }

    ResourceSourceJavaFileObject(URL resourceUrl) {
      this(resourceUrl, URI.create(resourceUrl.toString()));
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors)
        throws IOException {
      return resourceByteSource.asCharSource(Charset.defaultCharset()).read();
    }

    @Override
    public InputStream openInputStream() throws IOException {
      return resourceByteSource.openStream();
    }

    @Override
    public Reader openReader(boolean ignoreEncodingErrors) throws IOException {
      return resourceByteSource.asCharSource(Charset.defaultCharset()).openStream();
    }
  }
}
