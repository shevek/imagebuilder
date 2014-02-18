package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.Nonnull
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.Partition;
// import com.redhat.et.libguestfs.EventCallback;
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

// @Category(ImageTask)
class TarOperations implements Operations {

	static class Tar {

		String outputFileName = 'image'

		void outputFile(String name) {
			outputFileName = name
		}

	}

	public void tar(Closure c) {
		Tar t = new Tar()
		t.with c
		customize {
			_tar(delegate, t)
		}
	}

	void _tar(ImageTask.Context c, Tar t) {
		GuestFS g = c as GuestFS
		String outputFileName = t.outputFileName + ".tar.gz"
		File outputFile = new File(getOutputDir(), outputFileName)
		g.tgz_out("/", outputFile.absolutePath)
	}

}
