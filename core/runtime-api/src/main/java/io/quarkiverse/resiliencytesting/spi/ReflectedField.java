package io.quarkiverse.resiliencytesting.spi;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.security.CodeSource;

/**
 * One private field of a class of another extension, read because that class offers no other way to the value;
 * it is never written, a final field least of all. Every such dependency on the inside of a class goes through
 * here, declared as a constant next to the reason for it, so that they are easy to find, and so that one that no
 * longer holds, after an upgrade of the other extension, fails with a message that says which class changed and
 * where it was loaded from.
 *
 * @param <T> the type of the field
 */
public final class ReflectedField<T> {

    private final Class<?> declaringClass;
    private final String fieldName;
    private final Class<T> fieldType;

    private ReflectedField(Class<?> declaringClass, String fieldName, Class<T> fieldType) {
        this.declaringClass = declaringClass;
        this.fieldName = fieldName;
        this.fieldType = fieldType;
    }

    /**
     * The field {@code fieldName} that {@code declaringClass} itself declares, of exactly the type
     * {@code fieldType}. Nothing is looked up yet, so that a constant of a class can never fail to initialise.
     */
    public static <T> ReflectedField<T> of(Class<?> declaringClass, String fieldName, Class<T> fieldType) {
        return new ReflectedField<>(declaringClass, fieldName, fieldType);
    }

    /**
     * The value of the field in {@code holder}.
     *
     * @throws IllegalStateException if the class no longer has the field as declared here, or does not let it be
     *         read; the message says what to update
     */
    public T read(Object holder) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaringClass, MethodHandles.lookup());
            VarHandle field = lookup.findVarHandle(declaringClass, fieldName, fieldType);
            return fieldType.cast(field.get(holder));
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw changed(e);
        }
    }

    private IllegalStateException changed(Exception cause) {
        CodeSource source = declaringClass.getProtectionDomain().getCodeSource();
        return new IllegalStateException("network faults: the field '" + fieldName + "' (" + fieldType.getName() + ") of "
                + declaringClass.getName() + " cannot be read (" + cause + "). The class was loaded from "
                + (source == null ? "an unknown location" : source.getLocation())
                + ". Quarkus Resiliency Testing uses this field to put a proxy in front of a client that is built "
                + "in code, and was written for a version of the class that had it: the ReflectedField that names "
                + "it needs updating.", cause);
    }

    @Override
    public String toString() {
        return declaringClass.getName() + "#" + fieldName;
    }
}
