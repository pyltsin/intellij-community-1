import java.util.Random;
import java.util.stream.Stream;

public class ConsumedStream {
  public static void test1() {
    Stream<String> stream = Stream.of("x");
    stream.forEach(System.out::println);
    <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
  }

  public static void test2() {
    Stream<String> stream = Stream.of("x");
    Stream<String> stream2 = stream.filter(String::isEmpty);
    Stream<String> stream3 = <warning descr="Stream has already been linked or consumed">stream</warning>.filter(x -> !x.isEmpty());
  }

  public static void test3() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test4() {
    Stream<String> stream = Stream.of("x");
    stream.forEach(System.out::println);
    if (Math.random() > 0.5) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test5() {
    Stream<String> stream = Stream.of("x");
    switch (new Random().nextInt(10)) {
      case 0:
        stream.forEach(System.out::println);
      case 1:
        <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test6() {
    Stream<String> stream = Stream.of("x");
    try {
      stream.forEach(System.out::println);
    } finally {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test7() {
    Stream<String> stream = Stream.of("x");
    try {
      stream.forEach(System.out::println);
    } catch (Exception e) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test1E() {
    Stream<String> stream = Stream.of("x");
    int x = new Random().nextInt(10);
    if (x < 2) {
      stream.forEach(System.out::println);
    }
    if (x < 1) {
      <warning descr="Stream has already been linked or consumed">stream</warning>.forEach(System.out::println);
    }
  }

  public static void test1N() {
    Stream<String> stream = Stream.of("x");
    if (Math.random() > 0.5) {
      stream.forEach(System.out::println);
    } else {
      stream.distinct().forEach(System.out::println);
    }
  }

  public static void test2N() {
    Stream<String> stream = Stream.of("x");
    stream = stream.filter(x -> !x.isEmpty());
    stream.forEach(System.out::println);
  }

  public static void test3N() {
    Stream<String> stream = Stream.of("x");
    System.out.println(stream.isParallel());
    stream.forEach(System.out::println);
  }

  public static void test4N() {
    Stream<String> stream = Stream.of("x");
    switch (new Random().nextInt(10)) {
      case 0:
        stream.forEach(System.out::println);
        break;
      case 1:
        stream.forEachOrdered(System.out::println);
    }
  }

  public static void test5N() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      if (Math.random() > 0.5) {
        stream.forEach(System.out::println);
        break;
      }
    }
  }

  public static void test1NE() {
    Stream<String> stream = Stream.of("x");
    boolean b = Math.random() > 0.5;
    if (b) {
      stream.forEach(System.out::println);
    }
    if (!b) {
      stream.forEach(System.out::println);
    }
  }

  public static void test2NE() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      if (i == 5) {
        stream.forEach(System.out::println);
      }
    }
  }

  public static void test3NE() {
    Stream<String> stream = Stream.of("x");
    int x = new Random().nextInt(10);
    if (x < 2) {
      stream.forEach(System.out::println);
    }
    if (x > 5) {
      stream.forEach(System.out::println);
    }
  }
}
