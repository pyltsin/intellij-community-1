// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
public class Test {

  public boolean test() {

    return Math.random() < .5;
  }

  public class B extends Main {

    public void foo() {
      if (super.test()) System.out.println("foo");
    }

    public void bar() {
      if (super.test()) System.out.println("bar");
    }
  }
}