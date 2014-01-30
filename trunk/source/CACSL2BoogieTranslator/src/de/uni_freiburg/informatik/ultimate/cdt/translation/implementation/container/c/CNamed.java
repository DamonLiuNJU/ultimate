/**
 * Describes a named type given in C.
 */
package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c;

import org.eclipse.cdt.core.dom.ast.IASTElaboratedTypeSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTName;
import org.eclipse.cdt.core.dom.ast.IASTNamedTypeSpecifier;

/**
 * @author Markus Lindenmann
 * @date 01.11.2012
 */
public class CNamed extends CType {
    /**
     * The type this named type is mapping to.
     */
    private final CType mappedType;

    /**
     * The name that is mapped.
     */
    private final String name;
    
    /**
     * Constructor.
     * 
     * @param decl
     *            the declaration to work on.
     * @param mappedType
     *            the type this named type is referring to.
     */
    public CNamed(String name, CType mappedType) {
//        super();
        super(false, false, false, false); //FIXME: integrate those flags
        this.name = name;
        this.mappedType = mappedType;
    }
    
    /**
     * Getter for the named declaration's name.
     * 
     * @return the named declaration's name.
     */
    public String getName() {
    	return name;
    }

    /**
     * Getter for the directly mapped type.
     * 
     * @return the type this named type is referring to.
     */
    public CType getMappedType() {
        return mappedType;
    }

    /**
     * Getter for the real underlying type.
     * 
     * @return the type this named type is referring to.
     */
    public CType getUnderlyingType() {
        CType underlying = mappedType;
        while (underlying instanceof CNamed) {
            underlying = ((CNamed) underlying).getMappedType();
        }
        return underlying;
    }

    @Override
    public String toString() {
        return getName();
    }
    
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CType)) {
            return false;
        }
        return getUnderlyingType().equals(o);
    }
}
