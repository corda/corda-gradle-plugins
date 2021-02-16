package net.corda.plugins.javac.quasar;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.util.Filter;
import com.sun.tools.javac.util.Name;

public class ASTUtils {
    public static Iterable<Symbol> memberByName(Symbol.TypeSymbol typeSymbol, Name name, Filter<Symbol> predicate) {
        return typeSymbol.members().getSymbolsByName(name, predicate);
    }
}
