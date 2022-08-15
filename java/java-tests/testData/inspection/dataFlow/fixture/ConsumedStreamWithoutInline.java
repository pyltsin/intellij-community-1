import java.util.Iterator;
import java.util.Random;
import java.util.Spliterator;
import java.util.stream.Stream;

public class ConsumedStreamWithoutInline {
  public static void test1() {
    Stream<String> stream = Stream.of("x");
    Iterator<String> iterator = stream.iterator();
    Iterator<String> iterator2 = <warning descr="Stream has already been linked or consumed">stream</warning>.iterator();
  }

  public static void test2() {
    Stream<String> stream = Stream.of("x");
    Spliterator<String> spliterator = stream.spliterator();
    Iterator<String> iterator = <warning descr="Stream has already been linked or consumed">stream</warning>.iterator();
  }

  public static void test3() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      Iterator<String> iterator = <warning descr="Stream has already been linked or consumed">stream</warning>.iterator();
    }
  }

  public static void test4() {
    Stream<String> stream = Stream.of("x");
    stream.forEach(System.out::println);
    if (Math.random() > 0.5) {
      Iterator<String> iterator = <warning descr="Stream has already been linked or consumed">stream</warning>.iterator();
    }
  }

  public static void test5() {
    Stream<String> stream = Stream.of("x");
    switch (new Random().nextInt(10)) {
      case 0:
        Iterator<String> iterator = stream.iterator();
      case 1:
        Spliterator<String> spliterator = <warning descr="Stream has already been linked or consumed">stream</warning>.spliterator();
    }
  }

  public static void test6() {
    Stream<String> stream = Stream.of("x");
    try {
      Iterator<String> iterator = stream.iterator();
    } finally {
      Spliterator<String> spliterator = <warning descr="Stream has already been linked or consumed">stream</warning>.spliterator();
    }
  }

  public static void test7() {
    Stream<String> stream = Stream.of("x");
    try {
      Iterator<String> iterator = stream.iterator();
    } catch (Exception e) {
      Spliterator<String> spliterator = <warning descr="Stream has already been linked or consumed">stream</warning>.spliterator();
    }
  }

  public static void test1E() {
    Stream<String> stream = Stream.of("x");
    int x = new Random().nextInt(10);
    if (x < 2) {
      Iterator<String> iterator = stream.iterator();
    }
    if (x < 1) {
      Spliterator<String> spliterator = <warning descr="Stream has already been linked or consumed">stream</warning>.spliterator();
    }
  }

  public static void test1N() {
    Stream<String> stream = Stream.of("x");
    if (Math.random() > 0.5) {
      Iterator<String> iterator = stream.iterator();
    } else {
      Spliterator<String> spliterator = stream.spliterator();
    }
  }

  public static void test3N() {
    Stream<String> stream = Stream.of("x");
    System.out.println(stream.isParallel());
    Spliterator<String> spliterator = stream.spliterator();
  }

  public static void test4N() {
    Stream<String> stream = Stream.of("x");
    switch (new Random().nextInt(10)) {
      case 0:
        Iterator<String> iterator = stream.iterator();
        break;
      case 1:
        Spliterator<String> spliterator = stream.spliterator();
    }
  }

  public static void test5N() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      if (Math.random() > 0.5) {
        Iterator<String> iterator = stream.iterator();
        break;
      }
    }
  }

  public static void test1NE() {
    Stream<String> stream = Stream.of("x");
    boolean b = Math.random() > 0.5;
    if (b) {
      Spliterator<String> spliterator = stream.spliterator();
    }
    if (!b) {
      Iterator<String> iterator = stream.iterator();
    }
  }

  public static void test2NE() {
    Stream<String> stream = Stream.of("x");
    for (int i = 0; i < 10; i++) {
      if (i == 5) {
        Spliterator<String> spliterator = stream.spliterator();
      }
    }
  }

  public static void test3NE() {
    Stream<String> stream = Stream.of("x");
    int x = new Random().nextInt(10);
    if (x < 2) {
      Spliterator<String> spliterator = stream.spliterator();
    }
    if (x > 5) {
      Iterator<String> iterator = stream.iterator();
    }
  }
}
