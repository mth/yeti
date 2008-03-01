// ex: se sts=4 sw=4 expandtab:

/*
 * Yeti language compiler java bytecode generator.
 *
 * Copyright (c) 2007,2008 Madis Janson
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package yeti.lang.compiler;

import java.util.*;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.taskdefs.MatchingTask;
import org.apache.tools.ant.types.Path;
import org.apache.tools.ant.types.Parameter;

public class YetiTask extends MatchingTask {
    private List paths = new ArrayList();
    private java.io.File dir;
    private String[] preload = YetiC.PRELOAD;
    private String target;
    private Path classPath;

    public void setSrcDir(String dir) {
        this.dir = new java.io.File(dir);
    }

    public void setDestDir(String dir) {
        if (dir.length() != 0) {
            dir += '/';
        }
        target = dir;
    }

    public void setPreload(String preload) {
        this.preload = preload.length() == 0
            ? new String[0] : preload.split(":");
    }

    public Path createClasspath() {
        if (classPath == null) {
            classPath = new Path(getProject());
        }
        return classPath;
    }

    public void execute() {
        if (dir == null) {
            dir = getProject().getBaseDir();
        }
        String[] files = getDirectoryScanner(dir).getIncludedFiles();
        for (int i = files.length; --i >= 0; ) {
            files[i] = new java.io.File(dir, files[i]).getPath();
        }
        String[] classPath =
            this.classPath == null ? new String[0] : this.classPath.list();
        CodeWriter writer = new ToFile(target);
        YetiCode.CompileCtx compilation =
            new YetiCode.CompileCtx(new YetiC(), writer, preload,
                                    new ClassFinder(classPath));
        log("Compiling " + files.length + " files.");
        try {
            for (int i = 0; i < files.length; ++i) {
                compilation.compile(files[i], 0);
            }
            compilation.write();
        } catch (CompileException ex) {
            throw new BuildException(ex.getMessage());
        } catch (BuildException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            ex.printStackTrace();
            throw ex;
        } catch (Exception ex) {
            throw new BuildException(ex);
        }
    }
}
