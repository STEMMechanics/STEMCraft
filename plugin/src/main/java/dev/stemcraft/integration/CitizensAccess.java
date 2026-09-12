package dev.stemcraft.integration;

import java.lang.reflect.Method;

/** Shared optional Citizens API access; never references server internals. */
public final class CitizensAccess {
    /** @return whether Citizens is enabled and has an initialized implementation */
    public static boolean available() {
        if (!org.bukkit.Bukkit.getPluginManager().isPluginEnabled("Citizens")) return false;
        try { return (boolean) invokeStatic("net.citizensnpcs.api.CitizensAPI", "hasImplementation"); }
        catch (RuntimeException ignored) { return false; }
    }

    private CitizensAccess() {}
    public static Class<?> type(String name) {
        try {
            return Class.forName(name, true, CitizensAccess.class.getClassLoader());
        } catch (ClassNotFoundException ex) {
            throw new IllegalStateException("Citizens API is unavailable", ex);
        }
    }

    public static Object invokeStatic(String className, String method, Object... arguments) {
        return invoke(type(className), null, method, arguments);
    }

    public static Object invoke(Object target, String method, Object... arguments) {
        return invoke(target.getClass(), target, method, arguments);
    }

    public static Object invoke(Class<?> owner, Object target, String method, Object... arguments) {
        for (Method candidate : owner.getMethods()) {
            if (!candidate.getName().equals(method) || candidate.getParameterCount() != arguments.length) continue;
            try {
                return candidate.invoke(target, arguments);
            } catch (IllegalArgumentException ignored) {
                // Try another overload with the same arity.
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("Could not call Citizens method " + method, ex);
            }
        }
        throw new IllegalStateException("Citizens method not found: " + owner.getName() + "." + method);
    }
}
