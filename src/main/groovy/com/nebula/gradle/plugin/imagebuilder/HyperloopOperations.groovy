package com.nebula.gradle.plugin.imagebuilder

import com.redhat.et.libguestfs.GuestFS

// @Category(ImageTask)
class HyperloopOperations implements Operations {

	/** Assumed output by a gradle 'apply plugin: application' */
	void hyperloop_install(String srcDir) {
		upload {
			from "$srcDir/install"
			into '/opt/hyperloop'
		}
		upload {
			from "$srcDir/hyperloop/bin/hyperloop.conf"
			into '/etc/init'
		}
		rm "/var/log/upstart/hyperloop.log"
	}

	void hyperloop_args(String args) {
		set '/etc/default/hyperloop/HL_ARGS', args
		rm "/var/log/upstart/hyperloop.log"
	}

}
