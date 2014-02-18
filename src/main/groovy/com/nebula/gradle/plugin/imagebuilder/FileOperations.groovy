package com.nebula.gradle.plugin.imagebuilder

import com.redhat.et.libguestfs.GuestFS

// @Category(ImageTask)
class FileOperations implements Operations {

	// File customizations

	static class Transfer implements Serializable {
		Object from
		Object into

		def from(o) {
			from = o
		}

		def into(o) {
			into = o
		}
	}

	void upload(Closure c) {
		Transfer t = new Transfer()
		t.with c

		customize {
			GuestFS g = delegate as GuestFS
			Iterable<File> sourceFiles = project.files(t.from)
			// TODO: Really, if we have multiple sources
			// then targetFile MUST be a directory or
			// a nonexistent path which we can autocreate.
			// We should check that here by converting
			// sourceFiles to .files, and looking at size()
			// If sourceFiles.files.size() == 1 then
			// targetPath can be an existing directory or
			// a non-existing filename.
			String targetPath = t.into?.toString()
			sourceFiles.each { File sourceFile ->
				if (sourceFile.isDirectory()) {
					task.fsUpload(g, sourceFile, null, targetPath)
					return
				}
				if (g.is_dir(targetPath)) {
					String targetFile = "$targetPath/$sourceFile.name"
					g.upload(sourceFile.absolutePath, targetFile)
					return
				}
				g.upload(sourceFile.absolutePath, targetPath)
			}
		}
	}

	void download(Closure c) {
		Transfer t = new Transfer()
		t.with c

		customize {
			GuestFS g = delegate as GuestFS
			String sourceGlob = t.from.toString()
			String[] sourcePaths = g.glob_expand(sourceGlob)
			File targetFile = project.file(t.into)
			targetFile.parentFile?.mkdirs()
			sourcePaths.each {
				String sourceName = it.substring(it.lastIndexOf('/') + 1)
				if (g.is_dir(it)) {
					task.fsDownload(g)
				}
				else if (targetFile.isDirectory()) {
					File file = new File(targetFile, sourceName)
					g.download(sourcePaths[0], file.absolutePath)
				}
				else {
					g.download(sourcePaths[0], targetFile.absolutePath)
				}
			}
		}
	}

	void rm(String path) {
		customize {
			GuestFS g = delegate as GuestFS
			String[] paths = g.glob_expand(path)
			paths.each {
				g.rm_rf(it)
			}
		}
	}

	void checksum(String glob) {
		customize {
			GuestFS g = delegate as GuestFS
			g.glob_expand(glob).each {
				println "$it: " + g.checksum("md5", it)
			}
		}
	}

}
