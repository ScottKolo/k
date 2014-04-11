package org.kframework.kil;

import org.kframework.kil.visitors.Visitor;
import org.w3c.dom.Element;

import aterm.ATermAppl;

/**
 * Represents a <br />
 * occurring for formatting in a bag of cells
 */
public class TermComment extends Term {

    public TermComment(Element element) {
        super(element);
    }

    public TermComment(TermComment termComment) {
        super(termComment);
    }

    public TermComment(ATermAppl element) {
        super(element);
    }

    @Override
    public String toString() {
        return "<br />";
    }

    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
        return visitor.visit(this, p);
    }

    @Override
    public Term shallowCopy() {
        return new TermComment(this);
    }

    @Override
    public int hashCode() {
        return 53;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null)
            return false;
        if (this == o)
            return true;
        if (!(o instanceof TermComment))
            return false;
        return true;
    }

}
