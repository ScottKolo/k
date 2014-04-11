package org.kframework.kil;

import org.kframework.kil.loader.JavaClassesFactory;
import org.kframework.kil.visitors.Visitor;
import org.kframework.utils.xml.XML;
import org.w3c.dom.Element;

import aterm.ATermAppl;

public class SetItem extends CollectionItem {

    public SetItem(Element element) {
        super(element);
        this.value = (Term) JavaClassesFactory.getTerm(XML.getChildrenElements(element).get(0));
    }

    public SetItem(ATermAppl atm) {
        super(atm);
        value = (Term) JavaClassesFactory.getTerm(atm.getArgument(0));
    }

    public SetItem(SetItem node) {
        super(node);
    }

    public SetItem(Term node) {
        super("SetItem");
        this.value = node;
    }

    public String toString() {
        return this.value.toString();
    }
    
    @Override
    public <P, R> R accept(Visitor<P, R> visitor, P p) {
        return visitor.visit(this, p);
    }

    @Override
    public SetItem shallowCopy() {
        return new SetItem(this);
    }

}
