// Copyright (c) 2015-2016 K Team. All Rights Reserved.
package org.kframework.kore.compile;

import org.kframework.compile.ConfigurationInfo;
import org.kframework.compile.LabelInfo;
import org.kframework.definition.Context;
import org.kframework.definition.Rule;
import org.kframework.definition.Sentence;
import org.kframework.kil.Attribute;
import org.kframework.kore.K;
import org.kframework.kore.KApply;
import org.kframework.kore.KLabel;
import org.kframework.kore.KList;
import org.kframework.kore.KRewrite;
import org.kframework.kore.Unapply;

import java.util.List;
import java.util.stream.Stream;
import static org.kframework.definition.Constructors.*;
import static org.kframework.kore.KORE.*;

/**
 * This pass adds the implicit top and k cells to
 * the bodies of rules and contexts.
 * A K cell is added only if the body is a single item,
 * which is not already a cell or a rewrite on cells.
 * The top cell is added unless the body is already an
 * instance of the top cell.
 * Rules with the anywhere attribute are not modified.
 */
// TODO: rules defining functions shouldn't be wrapped
public class AddTopCellToRules {

    private final ConfigurationInfo cfg;
    private final LabelInfo labelInfo;

    public AddTopCellToRules(ConfigurationInfo cfg, LabelInfo labelInfo) {
        this.cfg = cfg;
        this.labelInfo = labelInfo;
    }

    public K addImplicitCells(K term) {
        if (labelInfo.isFunction(term)) return term;
        return addRootCell(term);
    }

    protected K addRootCell(K term) {
        KLabel root = cfg.getCellLabel(cfg.getRootCell());

        if (term instanceof KApply && ((KApply) term).klabel().equals(root)) {
            return term;
        } else {
            if (term instanceof KRewrite &&
                    ((KRewrite) term).left() instanceof KApply &&
                    ((KApply)((KRewrite) term).left()).klabel().equals(root)){
// CS427 TODO::
                // To Mo Tao:
                // We need to fix things like this:
                // term is a rewrite rule: something => something
                // and the two somthings are top level cells, i.e., HCells
                // <H> ... </H> => <H> ... </H>
                // The ... could be a list of something, we refer to then as left and right:
                // term is <H> left </H> => <H> right </H>

                // We used to call the following ImcompleteCellUtils.make to make a new rule by
                // wrapping it with the missing outer cells. So from the term we get
                // <H> <H> left </H> => <H> right </H> </H>
                // which makes the error.

                // 现在我们的做法是，如果我们发现term本身就是一个toplevel的cell的rewrite rull，我们就
                // 把他的左边 <H> left </H> and right handside <H> right </H> 变成 left and right.
                // And we make a new rule left => right (by calling KRewrite. it seems that KRewrite
                // is a scala function so you can call it like below, no new keyword)
                // this new rule is called rl, then we call IncompleteCellUtils.

                // You can follow this idea to make sure that we fix the bug. TODO::You can try old tests
                // TODO:: to make sure that the fixing does not break things, etc.

                KApply left = (KApply) ((KApply) ((KRewrite) term).left()).klist().stream().toArray()[1];
                KApply right = (KApply) ((KApply) ((KRewrite) term).right()).klist().stream().toArray()[1];
                KRewrite rl = KRewrite(left, right, term.att());
                return IncompleteCellUtils.make(root, true, rl, true);
            }
            return IncompleteCellUtils.make(root, true, term, true);
        }
    }

    public Rule addImplicitCells(Rule rule) {
        return new Rule(
                addImplicitCells(rule.body()),
                rule.requires(),
                rule.ensures(),
                rule.att());
    }

    public Context addImplicitCells(Context context) {
        return new Context(
                addImplicitCells(context.body()),
                context.requires(),
                context.att());
    }

    public Sentence addImplicitCells(Sentence s) {
        if (s.att().contains(Attribute.MACRO_KEY) || s.att().contains(Attribute.ANYWHERE_KEY)) {
            return s;
        }
        if (s instanceof Rule) {
            return addImplicitCells((Rule) s);
        } else if (s instanceof Context) {
            return addImplicitCells((Context) s);
        } else {
            return s;
        }
    }
}
