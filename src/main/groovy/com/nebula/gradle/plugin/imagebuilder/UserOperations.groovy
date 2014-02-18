package com.nebula.gradle.plugin.imagebuilder

import javax.annotation.Nonnull
import org.gradle.api.tasks.Input
import com.google.common.base.Charsets
import com.redhat.et.libguestfs.GuestFS

// @Category(ImageTask)
class UserOperations implements Operations {

	// User management

	@Input
	static int uidMin = 500
	@Input
	static int uidMax = 30000

	static class User {
		String name
		String password
		int uid = -1
		int gid = -1
		String group
		String gecos
		String home
		String shell

		List<String> groups = []

	}

	void user(@Nonnull Map data, @Nonnull Closure details) {
		User u = new User(data)
		u.with details
		customize {
			_user(delegate, u)
		}
	}

	void user(@Nonnull Map data) {
		user(data) { }
	}

	void user(@Nonnull String name, @Nonnull Closure details) {
		user(name: name, details)
	}

	void user(@Nonnull String name) {
		user(name: name) { }
	}

	void _user(ImageTask.Context c, User u) {
		GuestFS g = c as GuestFS
		if (!g.aug_match("/files/etc/passwd/${u.name}/uid")) {
			// g.aug_insert("/files/etc/passwd/*[last()]", u.name, false)

			if (u.uid < 0) {
				u.uid = uidMin
				g.aug_match("/files/etc/passwd//uid").each {
					int uid = g.aug_get(it) as int
					// Avoid hitting 'nobody' and 'nogroup' users
					if (uid >= u.uid && uid < uidMax)
						u.uid = uid + 1
				}
				// println "Creating ${u.name} using ${u.uid}"
			}

			// This may not be empty in Augeas.
			u.gecos = u.gecos ?: "Created by ImageBuilder"
			u.group = u.group ?: "users"
			u.home = u.home ?: "/home/${u.name}"
			u.shell = u.shell ?: "/bin/false"
		}

		if (u.group) {
			if (g.aug_match("/files/etc/group/${u.group}/gid"))
				u.gid = g.aug_get("/files/etc/group/${u.group}/gid") as int
			else
				// TODO: Create it.
				throw new IllegalArgumentException("No group ${u.group}")
		}

		if (u.password != null ||
				!g.aug_match("/files/etc/passwd/${u.name}/password"))
			g.aug_set("/files/etc/passwd/${u.name}/password", "x")
		if (u.uid >= 0)
			g.aug_set("/files/etc/passwd/${u.name}/uid", u.uid as String)
		else
			u.uid = g.aug_get("/files/etc/passwd/${u.name}/uid") as int
		if (u.gid >= 0)
			g.aug_set("/files/etc/passwd/${u.name}/gid", u.gid as String)
		else
			u.gid = g.aug_get("/files/etc/passwd/${u.name}/gid") as int
		if (u.gecos != null)
			g.aug_set("/files/etc/passwd/${u.name}/name", u.gecos)
		if (u.home != null)
			g.aug_set("/files/etc/passwd/${u.name}/home", u.home)
		if (u.shell != null)
			g.aug_set("/files/etc/passwd/${u.name}/shell", u.shell)

		if (u.password != null) {
			String passhash = qx(["mkpasswd", "-5", u.password])
			/*
			String passhash = qx([ "openssl",
				"passwd", "-1",
				"-salt", "ABCDEFGH",
				u.password
			])
			*/
			boolean found = false
			def lines = g.read_lines("/etc/shadow").collect { line ->
				def words = line.split(':', -1)
				if (words[0] == u.name) {
					words[1] = passhash
					line = words.join(':')
					found = true
				}
				return line
			}
			if (!found) {
				lines += [ "${u.name}:$passhash::0:99999:7:::" ]
			}
			String text = lines.join("\n") + "\n"
			byte[] bytes = text.getBytes(Charsets.ISO_8859_1)
			g.write("/etc/shadow", bytes)
		}

		if (u.home != null) {
			g.mkdir_p(u.home)
			g.chown(u.uid, u.gid, u.home)
		}

		u.groups.each { group ->
			// println "Searching group $group"
			if (!g.aug_match("/files/etc/group/$group/user").any {
				def member = g.aug_get(it)
				// println "Found member $member"
				return member == u.name
			}) {
				// println "Adding member"
				g.aug_insert("/files/etc/group/$group/*[self::gid or self::user][last()]", "user", false)
				g.aug_set("/files/etc/group/$group/user[last()]", u.name)
			}
		}
	}

}
