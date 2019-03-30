package com.softwaremill.quicklens

import com.softwaremill.quicklens.TestData._
import org.scalatest.{FlatSpec, Matchers}

class LensLazyTest extends FlatSpec with Matchers {
  it should "create reusable lens of the given type" in {
    val lens = modify[A1](_.a2.a3.a4.a5.name)

    val lm = lens.using(duplicate)
    lm(a1) should be(a1dup)
  }

  it should "compose lens" in {
    val lens_a1_a3 = modify[A1](_.a2.a3)
    val lens_a3_name = modify[A3](_.a4.a5.name)

    val lm = (lens_a1_a3 andThenModify lens_a3_name).using(duplicate)
    lm(a1) should be(a1dup)
  }
}
