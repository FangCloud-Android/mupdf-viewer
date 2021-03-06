/*
 * Copyright (C) 2016 Bartosz Schiller.
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
package com.artifex.mupdf.pdfviewer.source;

import android.content.Context;

import com.artifex.mupdf.pdfviewer.MuPDFCore;

import java.io.File;
import java.io.IOException;

public class FileSource implements DocumentSource {

    private File file;

    public FileSource(File file) {
        this.file = file;
    }

    @Override
    public MuPDFCore createDocument(Context context, String password) throws IOException {
        MuPDFCore document = new MuPDFCore(file.getAbsolutePath());
        if (document.needsPassword()) {
            document.authenticatePassword(password);
        }
        return document;
    }
}
