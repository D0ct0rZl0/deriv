package com.deriv.expression;

import com.deriv.expression.simplifier.MultSimplifier;
import java.util.*;
import java.util.concurrent.RecursiveTask;

import static com.deriv.expression.Add.*;
import static com.deriv.expression.Power.*;
import static java.util.stream.Collectors.toList;
import static com.deriv.expression.Constant.*;

/**
 * A Mult represents several factors, multiplied together.
 *
 * Data definition: a Mult is a list of Expressions (factors). This is analogous
 * to the factors in an expression.
 */
public class Mult implements Expression {
  /**
   * A list of factors to be multiplied together.
   */
  private List<Expression> _factors;

  /**
   * Instantiates a Mult. Avoid using as much as possible! Use the easy constructor
   * instead.
   */
  private Mult(List<Expression> _factors) {
    this._factors = _factors;
  }

  /**
   * Static constructor for a Mult object.
   * @param factors list of expressions
   * @return Expression
   */
  public static Expression mult(List<Expression> factors) {
    if (factors.isEmpty()) {
      // bad
      throw new RuntimeException("Don't instantiate a term with an empty list!");
    }

    return new MultSimplifierComplete(factors).simplifyToExpression();
  }

  /**
   * Static constructor for a Mult object.
   * @param factors array of expressions
   * @return Expression
   */
  public static Expression mult(Expression... factors) {
    return mult(Arrays.asList(factors));
  }

  /**
   * Static constructor for division.
   * @param numerator input
   * @param denominator input
   * @return Expression
   */
  public static Expression div(Expression numerator, Expression denominator) {
    return mult(numerator, poly(denominator, -1));
  }

  /**
   * Static method for negating an Expression (i.e. multiplying it by zero)
   * @param expr input
   * @return Expression
   */
  public static Expression negate(Expression expr) {
    return mult(constant(-1), expr);
  }

  /**
   * Getter method for the factors.
   * @return factors
   */
  public List<Expression> getFactors() {
    return _factors;
  }

  @Override
  public Expression getConstantFactor() {
    List<Expression> constants = _factors.stream()
                                    .filter(x -> x.isConstant()
                                                     || (x.getBase().isConstant()
                                                             && x.getExponent().equals(constant(-1))))
                                    .collect(toList());

    return constants.isEmpty()
               ? multID()
               : mult(constants);
  }

  @Override
  public Expression getSymbolicFactors() {
    List<Expression> symbolic = _factors.stream()
                                    .filter(x -> !x.isConstant()
                                                     && !(x.getBase().isConstant()
                                                             && x.getExponent().equals(constant(-1))))
                                    .collect(toList());
    return symbolic.isEmpty()
               ? multID()
               : mult(symbolic);
  }

  @Override
  public Boolean isNegative() {
    Expression constantFactor = this.getConstantFactor();

    return constantFactor.getNumerator().isNegative()
               || constantFactor.getDenominator().isNegative();
  }

  @Override
  public Expression getNumerator() {
    List<Expression> num = _factors.stream()
                               .filter(x -> !x.getExponent().isNegative())
                               .collect(toList());
    return num.isEmpty() ? multID() : mult(num);
  }

  @Override
  public Expression getDenominator() {
    List<Expression> den = _factors.stream()
                               .filter(x -> x.getExponent().isNegative())
                               .map(y -> poly(y, -1))
                               .collect(toList());

    return den.isEmpty() ? multID() : mult(den);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;

    if (!(o instanceof Mult))
      return false;

    Mult mul = (Mult) o;
    return mul._factors.equals(_factors);
  }

  @Override
  public int hashCode() {
    return 31 * _factors.hashCode() + 78;
  }

  @Override
  public String toString() {
    Expression den = this.getDenominator();

    if (!den.equals(multID())) {
      Expression num = this.getNumerator();

      // probably the easiest way to write this
      return num.toString() + " / " + den.toString();
    }

    return "(" + _factors.get(0).toString()
               + _factors.subList(1, _factors.size()).stream() //  sublist is O(1)
                     .map(Expression::toString)
                     .reduce("", (a, b) -> a + " * " + b)
               + ")";
  }

  @Override
  public String toLaTex() {
    Expression den = this.getDenominator();

    if (!den.equals(multID())) {
      Expression num = this.getNumerator();

      // probably the easiest way to write this
      return "\\frac{" + num.toLaTex() + "}{" + den.toLaTex() + "}";
    }

    return _factors.get(0).toLaTex()
             + _factors.subList(1, _factors.size()).stream() //  sublist is O(1)
                 .map(ex -> ex.isAdd() ? "(" + ex.toLaTex() + ")" : ex.toLaTex())
                 .reduce("", (a, b) -> a + b);
  }

  @Override
  public Optional<Expression> evaluate(Variable var, Expression input) {
    // multiplies terms together
    return Optional.of(_factors.parallelStream()
                         .map(x -> x.evaluate(var, input))
                         .filter(Optional::isPresent)
                         .map(Optional::get)
                         .collect(toList()))
             .flatMap(lst -> lst.size() == _factors.size()
                               ? Optional.of(mult(lst))
                               : Optional.empty());
  }

  @Override
  public Optional<Expression> differentiate(Variable var) {
    /*
     * Note: my unit tests suggest that this algorithm is faster than its
     * sequential version. See com.deriv.expresssion.ParallelTest.java for a
     * description of the sequential version.
     */
    return new ParallelMultDerivative(_factors, var).compute();
  }

  /**
   * RecursiveTask to compute the derivative of a Mult in parallel.
   */
  private static class ParallelMultDerivative extends RecursiveTask<Optional<Expression>> {
    /**
     * Input list of factors.
     */
    private List<Expression> factorList;

    /**
     * Input variable to differentiate with respect to.
     */
    private Variable var;

    /**
     * Private constructor for a ParallelMultDerivative.
     * @param factorList input list of expressions
     * @param var with respect to
     */
    private ParallelMultDerivative(List<Expression> factorList, Variable var) {
      this.factorList = factorList;
      this.var = var;
    }

    @Override
    public Optional<Expression> compute() {
      if (factorList.size() < 1) // illegal case
        return Optional.empty();

      if (factorList.size() == 1) // base case
        return factorList.get(0).differentiate(var);

      // always compute product rule down the middle of the list of factors
      int mid = factorList.size() / 2;

      // compute derivatives
      RecursiveTask<Optional<Expression>> task = new ParallelMultDerivative(factorList.subList(0, mid), var);
      task.fork(); // fork the first derivative
      Optional<Expression> secondDerivative = new ParallelMultDerivative(factorList.subList(mid, factorList.size()), var).compute();
      Optional<Expression> firstDerivative = task.join();

      // combine the derivatives together
      return firstDerivative
               .flatMap(x -> secondDerivative
                               .map(y ->
                                      add(
                                        mult(mult(factorList.subList(mid, factorList.size())), x),
                                        mult(y, (mult(factorList.subList(0, mid))))
                                      )));
    }
  }

  private static class MultSimplifierComplete extends MultSimplifier {
    /**
     * Constructor for a MultSimplifierComplete.
     * @param unFactors input
     */
    MultSimplifierComplete(List<Expression> unFactors) {
      super(unFactors);
    }

    @Override
    public Expression toExpression() {
      if (unFactors.size() == 2) {
        List<Expression> con = unFactors.stream()
                                 .filter(Expression::isConstant)
                                 .collect(toList());

        List<Expression> remain = unFactors.stream()
                                    .filter(Expression::isAdd)
                                    .collect(toList());

        if (con.size() == 1 && remain.size() == 1) {
          // distribute the constant amongst the terms
          return add(
            remain.get(0).asAdd().getTerms().stream()
              .map(x -> mult(con.get(0), x))
              .collect(toList()));
        }
      }

      // sort the _factors
      List<Expression> simplified = unFactors.stream().sorted().collect(toList());
      return simplified.size() > 1 ? new Mult(simplified) : simplified.get(0);
    }
  }
}