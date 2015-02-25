package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.Nonnull
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.Partition;
// import com.redhat.et.libguestfs.EventCallback;
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

// @Category(ImageTask)
class ExportOperations implements Operations {

	public void export(Closure c) {
		ExportSystem e = new ExportSystem();
		e.with c
		customize {
			ExportUtils.export(delegate, e)
		}
	}
}