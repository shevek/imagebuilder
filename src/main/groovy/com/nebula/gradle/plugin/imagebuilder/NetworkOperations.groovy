package com.nebula.gradle.plugin.imagebuilder

import com.redhat.et.libguestfs.GuestFS;

// @Category(ImageTask)
class NetworkOperations implements Operations {

	// Host and network management

	void hostname(String name) {
		customize {
			GuestFS g = delegate as GuestFS
			g.aug_set('/files/etc/hostname/hostname', name)

			// This might be better done using *[ipaddr = '127.0.1.1']
			// http://docs.puppetlabs.com/guides/augeas.html#paths-for-numbered-items
			def updated = g.aug_match('/files/etc/hosts//ipaddr').find {
				if (g.aug_get(it) == '127.0.1.1') {
					String path = (it - ~/ipaddr$/) + "canonical"
					g.aug_set(path, name)
					return true
				}
				return false
			}
			if (updated == null) {
				g.aug_set('/files/etc/hosts/01/ipaddr', '127.0.1.1')
				g.aug_set('/files/etc/hosts/01/canonical', name)
			}
		}
	}

	void network_interface(String name, String address=null, String netmask="255.255.255.0") {
		// network - this needs a special routine
		set "/etc/network/interfaces/auto[child::1 = '$name']/1", name
		set "/etc/network/interfaces/iface[. = '$name']", name
		set "/etc/network/interfaces/iface[. = '$name']/family", "inet"
		if (address == null) {
			set "/etc/network/interfaces/iface[. = '$name']/method", "dhcp"
		} else {
			set "/etc/network/interfaces/iface[. = '$name']/address", address
			set "/etc/network/interfaces/iface[. = '$name']/netmask", netmask
		}
	}

}
