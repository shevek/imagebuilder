/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.nebula.gradle.plugin.imagebuilder;

import java.io.File;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;

/**
 *
 * @author shevek
 */
public class CleanTask extends AbstractImageTask {

    public CleanTask() {
        upToDateWhenFalse();
    }

    private void umount(@Nonnull File root, @CheckForNull String name) {
        final File dir = name != null ? new File(root, name) : root;
        getProject().exec(new Action<ExecSpec>(){
            @Override
            public void execute(ExecSpec t) {
                t.commandLine("sudo", "umount", dir.getAbsolutePath());
                t.setIgnoreExitValue(true);
            }
        });
    }

	@TaskAction
	// @Override
	public void run() {
		File tmpDir = getTmpDir();

		File imageDir = new File(tmpDir, "root");
        umount(imageDir, "proc");
        umount(imageDir, "sys");
        umount(imageDir, "dev");
        umount(tmpDir, null);

        getProject().delete(getProject().getBuildDir());
	}
}