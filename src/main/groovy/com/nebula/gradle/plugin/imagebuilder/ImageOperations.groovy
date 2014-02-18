package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.Nonnull
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile

// @Category(ImageTask)
class ImageOperations implements Operations {

	static class Create {
		@Input
		long size
		@Input
		String mkfsCommand = 'mkfs.ext4'
		@Input
		List<String> mkfsArgs = [ '-F' ]
	}

	void create(@Nonnull Closure details) {
		Create c = new Create()
		c.with details
		customize {
			File imageFile = delegate as File
			imageFile.parentFile?.mkdirs()
			RandomAccessFile f = new RandomAccessFile(imageFile, "rw")
			f.setLength(c.size)
			f.seek(0)
			f.write(new byte[4096])
			f.close()

			def mkfsCommandLine = [ c.mkfsCommand ] + c.mkfsArgs + [ imageFile ]
			project.exec {
				commandLine mkfsCommandLine
			}
		}
	}
}
