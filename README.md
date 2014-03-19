# Introduction

This is a golden image builder for virtual or physical machine images.

The requirements are:
	Fast, with low memory requirements.
	Builds a golden image, rather than requiring a prior.
	Does not require root.
	Does not create risk to the host system from untrusted images or binaries.
	Does not boot the target machine.
	Can create Linux or Windows, foreign or native binary formats.
	Able to edit existing images as well as create new ones.

# Install requirements:

	sudo apt-get install apt-cacher-ng augeas-tools debootstrap \
		fakeroot libguestfs-tools libguestfs-java openjdk-7-jre \
		whois qemu-kvm
	sudo usermod -a -G kvm $USER
	sudo update-guestfs-appliance	# Can be run as user?
	sudo chmod 0644 /boot/vmlinuz*
	sudo mkdir -p /usr/lib/jni
	sudo ln -s /usr/lib/x86_64-linux-gnu/jni/libguestfs_jni.so /usr/lib/jni/

Note: if you are running imagebuilder inside a VM, ensure that nested
virutalization is supported by your hypervisor and guest OS. You can
check the following command to determine if nested virtualization
is supported:

	kvm-ok	# Look for "KVM acceleration can be used" in output.

# Installation

	libguestfs-test-tool
	./gradlew install

# Use

	cd examples/demo-ubuntu
	../../gradlew --stacktrace clean build exportRaw

For faster operation, do:

	cd demo
	../../gradlew --stacktrace clean tmpfs build exportRaw

# Conventions

Task names:
* tmpfs
* export{Format}
* build
* clean

# Documentation

The core task is ImageTask, which interprets a list of customizations.
Raw customizations may be written in customize {} blocks. All other
keywords are a DSL defined in the Operations classes, which eventually
compile down to customize{} blocks.

The Operations classes define methods which are exposed in the DSL
of the ImageTask. You can write a new Operations class and load it
at the top of an image{} block to make the keywords it defines
accessible.

You might want to:
	Create an image file.
	Debootstrap into it.
	Customize it.
	Convert it to output as an AMI.

# Internals and Development

This is a wrapper around libguestfs and augeas.

To experiment with what functions are available in the configure{}
block, use guestfish:

	$ guestfish -a <hard-disk>
	> run
	> mount /dev/[root] /
	> (other mounts)
	(if desired)
	> mount-vfs '' sysfs /dev/null /sys
	> mount-vfs '' proc /dev/null /proc
	> mount-vfs '' devtmpfs /dev/null /dev
	(if you want to use augeas in guestfish)
	> aug-init / 0

When you have worked out what filesystem commands you want to use,
call:
	configure {
		GuestFS g = delegate as GuestFS
		g.<whatever>()
	}

To mess with system files, use augeas.

To experiment with augeas, on your local system, run augtool:

	$ augtool
	augtool> ls /files/etc/

Then port it to g.aug\_\*() calls in a configure block.

# TODO

Make ExportOperations compute the minimal size of the export image
given the partition image.

# Known issues

> LibGuestFSException: supermin-helper exited with error status 1.

You need to:
1) Run libguestfs-test-tool
2) Fix any issues it describes.
2a) Probably sudo chmod 0644 /boot/vmlinuz\*

> Could not find property 'ImageBuilder' on project ':myproject'.

You need to:
1) Add your subdirectory to ../settings.gradle.
2) Touch your buildfile (update the timestamp).
3) Rerun.

> Anything else:

Try running libguestfs-test-tool anyway.
