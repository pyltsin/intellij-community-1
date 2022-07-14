import java.time.format.DateTimeFormatter;

class Test {
  public static final String TT = "T" + "T";

  void test(boolean b) {
    DateTimeFormatter.ofPattern(<warning descr="Unsupported token: ' T '">TT</warning>);
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: ' { '">{</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: ' } '">}</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Unsupported token: ' # '">#</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Not found pattern for padding">p</warning>");
    DateTimeFormatter.ofPattern("<warning descr="Not found pattern for padding">p</warning>-");
    DateTimeFormatter.ofPattern("pMM");
    DateTimeFormatter.ofPattern(<warning descr="Character without a pair: ' ] '">"[[[][]]]]"</warning>);
    DateTimeFormatter.ofPattern("[[[][]]");
  }
}