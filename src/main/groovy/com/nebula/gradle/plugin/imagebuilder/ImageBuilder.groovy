package com.nebula.gradle.plugin.imagebuilder

import org.gradle.api.Plugin
import org.gradle.api.Project

class ImageBuilderExtension {
	Project project

	ImageBuilderExtension(Project project) {
		this.project = project
	}
}

class ImageBuilder implements Plugin<Project> {
	void apply(Project project) {

		// project.extensions.create('image', ImageBuilderExtension, project)

		project.task('tmpfs', type: TmpfsTask) {
			group = "imagebuilder"
		}
		project.task('setup', type: SetupTask) {
			mustRunAfter project.tmpfs
			group = "imagebuilder"
		}
		project.task('image', type: ImageTask) {
			mustRunAfter project.tmpfs
			group = "imagebuilder"
		}
		project.task('build') {
			dependsOn project.image
			group = "imagebuilder"
		}

		project.task('clean', type: CleanTask) {
			group = "imagebuilder"
		}

	}
}
