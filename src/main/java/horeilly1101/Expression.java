package horeilly1101;

import com.sun.istack.internal.NotNull;

import java.util.*;
import java.util.function.*;

import static java.util.stream.Collectors.toList;

interface Expression extends Comparable {
  /**
   * Plugs an input into the value of whatever variable is
   * in the function, and then evaluates the rest of the
   * expression.
   *
   * @return Expression solution
   */
  Expression evaluate(String var, Double input);

  /**
   * Takes the derivative of the given expression.
   *
   * @return Expression derivative
   */
  Expression differentiate(String var);

  /**
   * This method compares an expression with a given object. This
   * is important, as it allows us to define an ordering on our
   * data structures. It also makes equality less strict. (i.e.
   * 2.0 * x should be equal to x * 2.0.)
   */
  default int compareTo(@NotNull Object o) {
    // constants come first
    if (this.getClass().equals(Constant.class)) {
      return -1;
    } else if (o.getClass().equals(Constant.class)) {
      return 1;
    }

    return this.toString().compareTo(o.toString());
  }

  default Mult asMult() {
    return (Mult) this;
  }

  default Add asAdd() {
    return (Add) this;
  }

  default Power asPower() {
    return (Power) this;
  }

  default Expression getExponent() {
    return Constant.multID();
  }

  default Expression getBase() {
    return this;
  }

  default Constant getConstantFactor() {
    if (this.getClass().equals(Mult.class)) {
      List<Expression> cons = this.asMult().factors.stream()
                                  .filter(x -> x.getClass().equals(Constant.class))
                                  .collect(toList());
      return cons.isEmpty() ? new Constant(1.0) : (Constant) cons.get(0);
    } else {
      return new Constant(1.0);
    }
  }

  default Expression getRemainingFactors() {
    return this.getClass().equals(Mult.class)
               ? Mult.mult(this.asMult().factors.stream()
                               .filter(x -> !x.getClass().equals(Constant.class))
                               .collect(toList()))
               : this;
  }

  class Mult implements Expression {
    // we need to be able to store constants and factors separately
    private List<Expression> factors;
    private Constant constant;

    /**
     * Instantiates a Term. Avoid using as much as possible! Use the easy constructor
     * instead.
     *
     * Data definition: a term is a list of Expressions (factors). This is analogous
     * to the factors in an expression.
     */
    private Mult(List<Expression> factors) {
      this.factors = factors;
    }

    static Expression mult(List<Expression> factors) {
      if (factors.isEmpty()) {
        throw new RuntimeException("Don't instantiate a term with an empty list!");
      } else {
        // we don't want terms of one object
        List<Expression> simplified = simplify(factors);
        simplified.sort(Expression::compareTo);
        return simplified.size() > 1 ? new Mult(simplified) : simplified.get(0);
      }
    }

    static Expression mult(Expression... factors) {
      return mult(Arrays.asList(factors));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Mult)) {
        return false;
      }

      Mult mul = (Mult) o;
      return mul.factors.equals(this.factors);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 8;
    }

    @Override
    public String toString() {
      // factors should never be empty, but we're being suitably cautious
      return factors.isEmpty()
                 ? ""
                 // a little clunky, but it gets the job done
                 : "(" + factors.get(0).toString()
                       + factors.subList(1, factors.size()).stream() //  sublist is O(1)
                             .map(Expression::toString)
                             .reduce("", (a, b) -> a + " * " + b) + ")";
    }

    public Expression evaluate(String var, Double input) {
      // multiplies factors together
      return mult(factors.stream()
                 .map(x -> x.evaluate(var, input))
                 .collect(toList()));
    }

    static List<Expression> simplify(List<Expression> factors) {
      return simplifyConstants(withoutNesting(factors));
    }

    /**
     * This method simplifies a list of factors by ensuring factors
     * are taken to the proper exponents. (e.g. we want to write x * x
     * as x ^ 2.0.)
     *
     * @return List<Expression> simplified
     */
    static List<Expression> simplifyFactors(List<Expression> factors) {
      HashMap<Expression, List<Expression>> powerMap = new HashMap<>();

      for (Expression fac : factors) {
        if (powerMap.containsKey(fac.getBase())) {
          List<Expression> newList = powerMap.get(fac.getBase());
          newList.add(fac.getExponent());
          powerMap.replace(fac.getBase(), newList);
        } else {
          List<Expression> newList = new ArrayList<>();
          newList.add(fac.getExponent());
          powerMap.put(fac.getBase(), newList);
        }
      }

      System.out.println("powerMap " + powerMap.toString());

      // add up the exponents
      return powerMap.keySet().stream()
                 .map(key -> Power.power(
                     key,
                     Add.add(powerMap.get(key))))
                 .collect(toList());
    }

    /**
     * This method simplifies a list of factors to get rid of extraneous
     * constant factors. (e.g. multipling an expression by 1 should yield
     * the expression, multiplying an expression by 0 should yield zero,
     * and so on.)
     *
     * It also multiplies the values of all constants together, so that each
     * mult has a single Constant.
     *
     * @return List<Expression> simplified
     */
    static List<Expression> simplifyConstants(List<Expression> factors) {
      // keep track of constants' values
      List<Expression> noConstants = new ArrayList<>();
      Double constants = 1.0;

      for (Expression factor : factors) {
        if (factor.getClass().equals(Constant.class)) {
          // checked cast
          constants *= ((Constant) factor).val;
        } else {
          noConstants.add(factor);
        }
      }

      // multiplicative identity?
      if (constants == 1.0 && noConstants.isEmpty()) {
        noConstants.add(Constant.constant(1.0));
        // zero?
      } else if (constants == 0.0) {
        // all factors go to zero
        noConstants.clear();
        noConstants.add(Constant.constant(0.0));
      } else if (constants != 1.0) {
        noConstants.add(Constant.constant(constants));
      }

      return noConstants;
    }

    /**
     * This method simplifies a list of factors by taking advantage of
     * the associativity of multiplication. (i.e. a Mult object multiplied
     * by a Mult object should not yield a Mult object of two Mult objects.
     * It should yield a Mult object of whatever was in the original objects,
     * flatmapped together.)
     *
     * @return List<Expression> simplified
     */
    static List<Expression> withoutNesting(List<Expression> factors) {
      List<Expression> newList = new ArrayList<>();

      for (Expression factor : factors) {
        if (factor.getClass().equals(Mult.class)) {
          // checked cast
          newList.addAll(factor.asMult().factors);
        } else {
          newList.add(factor);
        }
      }

      return newList;
    }

    public Expression differentiate(String var) {
      return Add.add(
                     mult(
                         factors.get(0),
                         mult(factors.subList(1, factors.size())).differentiate(var)
                     ),
                     mult(
                         factors.get(0).differentiate(var),
                         mult(factors.subList(1, factors.size()))
                     ));
    }
  }

  class Add implements Expression {
    private List<Expression> terms;

    /**
     * Instantiates an Add. Avoid using as much as possible! Use the easy constructor
     * instead.
     *
     * Data definition: an add is a list of Expressions (terms). This is analogous to
     * the terms in an expression.
     */
    private Add(List<Expression> terms) {
      this.terms = terms;
    }

    static Expression add(List<Expression> terms) {
      if (terms.isEmpty()) {
        throw new RuntimeException("don't instantiate an expr with an empty list!");
      } else {
        List<Expression> simplified = simplify(terms);
        // reverse sort
        simplified.sort(Comparator.reverseOrder());
        return simplified.size() > 1 ? new Add(simplified) : terms.get(0);
      }
    }

    static Expression add(Expression... terms) {
      return add(Arrays.asList(terms));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Add)) {
        return false;
      }

      Add ad = (Add) o;
      return ad.terms.equals(this.terms);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 9;
    }

    @Override
    public String toString() {
      return terms.isEmpty()
                 ? ""
                 // a little clunky, but it gets the job done
                 : "[" + terms.get(0).toString()
                       + terms.subList(1, terms.size()).stream()
                             .map(Expression::toString)
                             .reduce("", (a, b) -> a + " + " + b)
                       + "]";
    }

    public Expression evaluate(String var, Double input) {
      // adds terms together
      return add(terms.stream()
                 .map(x -> x.evaluate(var, input))
                     .collect(toList()));
    }

    static List<Expression> simplify(List<Expression> terms) {
//      return simplifyConstants(withoutNesting(terms));
      return simplifyConstants(withoutNesting(terms));
    }

    /**
     * This method simplifies a list of terms by ensuring terms
     * are taken to the proper constants. (e.g. we want to write x + x
     * as 2.0 * x.)
     *
     * @return List<Expression> simplified
     */
    static List<Expression> simplifyTerms(List<Expression> terms) {

      HashMap<Expression, List<Double>> powerMap = new HashMap<>();

      for (Expression term : terms) {
        if (powerMap.containsKey(term.getRemainingFactors())) {
          List<Double> newList = powerMap.get(term.getRemainingFactors());
          newList.add(term.getConstantFactor().val);
          powerMap.replace(term.getRemainingFactors(), newList);
        } else {
          List<Double> newList = new ArrayList<>();
          newList.add(term.getConstantFactor().val);
          powerMap.put(term.getRemainingFactors(), newList);
        }
      }

      // add up the constants
      return powerMap.keySet().stream()
                 .map(key -> Mult.mult(
                     key,
                     Constant.constant(powerMap.get(key).stream()
                                           .reduce(0.0, (a, b) -> a + b))))
                 .collect(toList());
    }

    /**
     * This method simplifies a list of factors to get rid of extraneous
     * constant factors. (e.g. adding 0.0)
     *
     * @return List<Expression> simplified
     */
    static List<Expression> simplifyConstants(List<Expression> factors) {
      // keep track of constants' values
      List<Expression> noConstants = new ArrayList<>();
      Double constants = 0.0;

      for (Expression factor : factors) {
        if (factor.getClass().equals(Constant.class)) {
          // checked cast
          constants += ((Constant) factor).val;
        } else {
          noConstants.add(factor);
        }
      }

      // multiplicative identity?
      if (constants == 0.0 && noConstants.isEmpty()) {
        noConstants.add(Constant.constant(0.0));
        // zero?
      } else if (constants != 0.0) {
        noConstants.add(Constant.constant(constants));
      }

      return noConstants;
    }

    /**
     * This method simplifies a list of terms by taking advantage of
     * the associativity of addition. (i.e. a Mult object multiplied
     * by a Mult object should not yield a Mult object of two Mult objects.
     * It should yield a Mult object of whatever was in the original objects,
     * flatmapped together.)
     *
     * @return List<Expression> simplified
     */
    static List<Expression> withoutNesting(List<Expression> terms) {
      List<Expression> newList = new ArrayList<>();

      for (Expression term : terms) {
        if (term.getClass().equals(Add.class)) {
          // checked cast
          newList.addAll(term.asAdd().terms);
        } else {
          newList.add(term);
        }
      }

      return newList;
    }

    public Expression differentiate(String var) {
      // linearity of differentiation
      return add(terms.stream()
                     .map(x -> x.differentiate(var))
                     .collect(toList()));
    }
  }

  class Log implements Expression {
    Expression base;
    Expression result;

    private Log(Expression base, Expression result) {
      this.base = base;
      this.result = result;
    }

    static Expression log(Expression base, Expression result) {
      return new Log(base, result);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Log)) {
        return false;
      }

      Log log = (Log) o;
      return log.base.equals(this.base) && log.result.equals(this.result);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 8;
    }

    @Override
    public String toString() {
      return "log(" + base.toString() + ", " + result.toString() + ")";
    }

    public Expression evaluate(String var, Double val) {
      return log(base.evaluate(var, val), result.evaluate(var, val));
    }

    public Expression differentiate(String var) {
      return Mult.mult(
          Add.add(
              Mult.mult(
                  base,
                  result.differentiate(var),
                  log(Constant.e(), base)),
              Mult.mult(
                  Constant.constant(-1.0),
                  result,
                  base.differentiate(var),
                  log(Constant.e(), result))),

          Power.poly(
              Mult.mult(
                  result,
                  base,
                  Power.poly(
                      log(Constant.e(),
                          base),
                      Constant.constant(2.0))),
              Constant.constant(-1.0)));
    }
  }

  class Power implements Expression {
    private Expression base;
    private Expression exponent;

    /**
     * Instantiates a Power. Avoid using as much as possible! Use the easy constructor
     * instead. (A power is the more general form of a polynomial and an exponential.)
     *
     * Data definition: a power is a base and an exponent.
     */
    private Power(Expression base, Expression exponent) {
      this.base = base;
      this.exponent = exponent;
    }
//
//    static Power polyUnsimplified(Expression base) {
//      return new Power(base, 1.0);
//    }

    static Expression power(Expression base, Expression exponent) {
      if (exponent.equals(Constant.multID())) {
        return base;
      }
      return new Power(base, exponent);
    }

    static Expression poly(Expression base, Constant exponent) {
      return power(base, exponent);
    }

    static Expression exponential(Constant base, Expression exponent) {
      return new Power(base, exponent);
    }

    @Override
    public Expression getExponent() {
      return exponent;
    }

    @Override
    public Expression getBase() {
      return base;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Power)) {
        return false;
      }

      Power pow = (Power) o;
      return pow.base.equals(this.base) && pow.exponent.equals(this.exponent);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 12;
    }

    @Override
    public String toString() {
      return "(" + base.toString() + ") ^ (" + exponent.toString() + ")";
    }

    public Expression evaluate(String var, Double input) {
      return power(
          base.evaluate(var, input),
          exponent.evaluate(var, input));
    }

//    static Expression simplify(Expression base, Double exponent) {
//      if (exponent == 1.0) {
//        return base;
//      } else if (exponent == 0.0) {
//        return Constant.constant(0.0);
//      } else {
//        return new Power(base, exponent);
//      }
//    }

    public Expression differentiate(String var) {
      // UPDATE
      return this;
    }
  }

  class Trig implements Expression {

    // maps to ensure cleaner code (i.e. no long if statements)
    private Map<String, Function<Expression, Expression>> evalMap = new TreeMap<>();
    private Map<String, BiFunction<Trig, String, Expression>> derivMap = new TreeMap<>();

    private String func;
    private Expression inside;

    private Trig(String func, Expression inside) {
      this.func = func;
      this.inside = inside;

      // define functions for evaluating expressions
      evalMap.put("sin", Trig::sin);
      evalMap.put("cos", Trig::cos);
      evalMap.put("tan", Trig::tan);
      evalMap.put("csc", Trig::csc);
      evalMap.put("sec", Trig::sec);
      evalMap.put("cot", Trig::cot);

      // define functions for evaluating derivatives
      derivMap.put("sin",
          (x, var) -> Mult.mult(
              x.inside.differentiate(var),
              cos(inside)));

      derivMap.put("cos",
          (x, var) -> Mult.mult(
              Constant.constant(-1.0),
              x.inside.differentiate(var),
              sin(inside)));

      derivMap.put("tan",
          (x, var) -> Mult.mult(
              x.inside.differentiate(var),
              Power.poly(
                  sec(inside),
                  Constant.constant(2.0))));

      derivMap.put("csc",
          (x, var) -> Mult.mult(
              Constant.constant(-1.0),
              x.inside.differentiate(var),
              csc(inside),
              cot(inside)));

      derivMap.put("sec",
          (x, var) -> Mult.mult(
              x.inside.differentiate(var),
              sec(inside),
              tan(inside)));

      derivMap.put("cot",
          (x, var) -> Mult.mult(
              Constant.constant(-1.0),
              x.inside.differentiate(var),
              Power.poly(
                  csc(inside),
                  Constant.constant(2.0))));
    }

    static Expression sin(Expression inside) {
      return new Trig("sin", inside);
    }

    static Expression cos(Expression inside) {
      return new Trig("cos", inside);
    }

    static Expression tan(Expression inside) {
      return new Trig("tan", inside);
    }

    static Expression csc(Expression inside) {
      return new Trig("csc", inside);
    }

    static Expression sec(Expression inside) {
      return new Trig("sec", inside);
    }

    static Expression cot(Expression inside) {
      return new Trig("cot", inside);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Trig)) {
        return false;
      }

      Trig tri = (Trig) o;
      return tri.func.equals(this.func) && tri.inside.equals(this.inside);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 10;
    }

    @Override
    public String toString() {
      return this.func + "(" + this.inside.toString() + ")";
    }

    public Expression evaluate(String var, Double val) {
      return evalMap.get(this.func)
                 .apply(inside.evaluate(var, val));
    }

    public Expression differentiate(String var) {
      return derivMap.get(this.func).apply(this, var);
    }

  }

  class Constant implements Expression {
    private Double val;

    private Constant(Double val) {
      this.val = val;
    }

    static Constant constant(Double val) {
      return new Constant(val);
    }

    static Constant multID() {
      return constant(1.0);
    }

    static Constant addID() {
      return constant(0.0);
    }

    static Expression e() {
      return new Variable("e");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Constant)) {
        return false;
      }

      Constant con = (Constant) o;
      return con.val.equals(this.val);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 11;
    }

    @Override
    public String toString() {
      return val.toString();
    }

    public Expression evaluate(String var, Double input) {
      return this;
    }

    public Expression differentiate(String var) {
      return constant(0.0);
    }
  }

  class Variable implements Expression {
    String var;

    private Variable(String var) {
      this.var = var;
    }

    static Expression var(String var) {
      if (var.equals("e")) {
        throw new RuntimeException("Variable can't be named e.");
      }

      return new Variable(var);
    }

    static Expression x() {
      return new Variable("x");
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (!(o instanceof Variable)) {
        return false;
      }

      Variable var = (Variable) o;
      return var.var.equals(this.var);
    }

    @Override
    public int hashCode() {
      return this.toString().hashCode() + 12;
    }

    @Override
    public String toString() {
      return var;
    }

    public Expression evaluate(String var, Double input) {
      return var.equals(this.var) ? Constant.constant(input) : this;
    }

    public Expression differentiate(String wrt) {
      return wrt.equals(var) ? Constant.constant(1.0) : Constant.constant(0.0);
    }
  }
}