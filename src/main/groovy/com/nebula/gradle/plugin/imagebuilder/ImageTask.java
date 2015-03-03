package com.nebula.gradle.plugin.imagebuilder;

import javax.annotation.Nonnull;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.redhat.et.libguestfs.GuestFS;
import com.redhat.et.libguestfs.LibGuestFSException;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MetaClass;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import javax.annotation.CheckForNull;
import org.codehaus.groovy.reflection.MixinInMetaClass;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;

public class ImageTask extends AbstractImageTask {

    // @Input
    private final List<Action<Context>> customizations = new ArrayList<Action<Context>>();

    public ImageTask() {
        upToDateWhenFalse();
        setDescription("Builds an image.");
        load(ImageOperations.class, FileOperations.class, ExecuteOperations.class,
                DebianOperations.class,
                UserOperations.class, NetworkOperations.class,
                ExportOperations.class, TarOperations.class);
    }

    public void load(@Nonnull List<Class<? extends Operations>> operations) {
        // if (!(metaClass instanceof ExpandoMetaClass))
        for (Class<? extends Operations> operation : operations)
            DefaultGroovyMethods.mixin(getClass(), operation);
        // MetaClass metaClass = InvokerHelper.getMetaClass(getClass());
        // MixinInMetaClass.mixinClassesToMetaClass(metaClass, Lists.<Class>newArrayList(operations));
    }

    public void load(@Nonnull Class<? extends Operations>... operations) {
        load(Arrays.asList(operations));
    }

    public void customize(@Nonnull Action<Context> c) {
        customizations.add(c);
    }

    // Augeas customizations
    public void set(@Nonnull final String path, @Nonnull final String value) {
        customize(new Action<Context>() {
            @Override
            public void execute(Context t) {
                try {
                    GuestFS g = t.getGuestFS();
                    g.aug_set("/files" + path, value);
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        });
    }

    public void set(@Nonnull final Map<String, String> settings) {
        customize(new Action<Context>() {
            @Override
            public void execute(Context t) {
                try {
                    GuestFS g = t.getGuestFS();
                    for (Map.Entry<String, String> e : settings.entrySet()) {
                        g.aug_set("/files" + e.getKey(), e.getValue());
                    }
                } catch (Exception e) {
                    throw Throwables.propagate(e);
                }
            }
        });
    }

    public class Context extends GroovyObjectSupport implements AutoCloseable {

        private GuestFS g;

        // Makes the project keyword work in operations.
        @Nonnull
        public Project getProject() {
            return ImageTask.this.getProject();
        }

        @Nonnull
        public ImageTask getTask() {
            return ImageTask.this;
        }

        @Nonnull
        public GuestFS getGuestFS(@CheckForNull Action<GuestFS> c)
                throws LibGuestFSException {
            if (g == null) {
                g = fsOpen(c);
                fsInspect(g);
                g.aug_init("/", 0);
            }
            return g;
        }

        @Nonnull
        public GuestFS getGuestFS()
                throws LibGuestFSException {
            return getGuestFS(null);
        }

        @Nonnull
        public File getImageFile() throws LibGuestFSException {
            // Assume raw file manipulation.
            close();
            return getTask().getImageFile();
        }

        /*
         public QEmu getQEmu() {
         if (g != null) {
         close();
         }
         }
         */
        @Override
        public void close() throws LibGuestFSException {
            if (g != null) {
                try {
                    for (String key : g.aug_match("/augeas//error/message"))
                        getLogger().warn(key + " = " + g.aug_get(key));
                    g.aug_save();
                    g.aug_close();
                } catch (LibGuestFSException e) {
                    getLogger().warn("Failed to close augeas", e);
                }
                fsClose(g);
                g = null;
            }
        }

        // The purpose of this method is to return the
        // relevant resource type to the operation,
        // ensuring that all handles from other resource
        // types have been cleaned up, closed and flushed.
        // This allows the context to cache the guestfs
        // handle between operations.
        public Object asType(@Nonnull Class<?> c) throws LibGuestFSException {
            if (GuestFS.class.isAssignableFrom(c))
                return getGuestFS();
            if (File.class.isAssignableFrom(c))
                return getImageFile();
            return DefaultGroovyMethods.asType(this, c);
        }

        /*
         def methodMissing(String name, args) {
         getTask().invokeMethod(name, args)
         }
         */
    }

    @TaskAction
    // @Override
    public void run() throws Exception {
        Context c = new Context();
        try {
            for (Action<Context> customization : customizations)
                customization.execute(c);
        } finally {
            c.close();
        }
    }
}
