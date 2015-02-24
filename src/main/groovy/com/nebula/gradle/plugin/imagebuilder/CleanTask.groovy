package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.CheckForNull
import javax.annotation.Nonnull
import org.gradle.api.Project
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

class CleanTask extends AbstractImageTask {

	public CleanTask() {
		getOutputs().upToDateWhen { false }
	}

	@TaskAction
	// @Override
	void run() {
		File tmpDir = getTmpDir()

		def imageDir = new File(tmpDir, "root")
		[
			"$imageDir.absolutePath/proc",
			"$imageDir.absolutePath/sys",
			"$imageDir.absolutePath/dev",
			"$tmpDir.absolutePath"
		].each { dir ->
			project.exec {
				commandLine "sudo", "umount", dir
				ignoreExitValue true
			}
		}

		project.buildDir.deleteDir()
	}
}
