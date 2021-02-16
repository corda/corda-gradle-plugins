package net.corda.plugins.javac.quasar;

import com.sun.tools.javac.code.Scope;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.Name;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ASTUtils {
    private static final Method getTypeMembers;
    private static final Method filterClassMembers;

    static  {
        try {
            if (Float.parseFloat(System.getProperty("java.specification.version")) > 8) {
                filterClassMembers = Scope.class.getMethod("getSymbolsByName", Name.class, Filter.class);
            } else {
                filterClassMembers = Scope.class.getMethod("getElementsByName", Name.class, Filter.class);
            }
            getTypeMembers = Symbol.TypeSymbol.class.getMethod("members");
        } catch (NoSuchMethodException nsme) {
            throw new RuntimeException(nsme);
        }
    }

    public static Iterable<Symbol> memberByName(Symbol.TypeSymbol typeSymbol, Name name, Filter<Symbol> predicate) {
        try {
            return (Iterable<Symbol>) filterClassMembers.invoke(getTypeMembers.invoke(typeSymbol), name, predicate);
        } catch (IllegalAccessException | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }
}
