package DunceCap

import org.scalatest.FunSuite

import DunceCap.attr.Attr

import scala.collection.mutable

class GHDSolverTest extends FunSuite {

  final val RELATIONS: List[QueryRelation] = List(
    QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "b", "c")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("g", "a")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("c", "d", "e")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("e", "f")))
  final val PATH2: List[QueryRelation] = List(
    QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "b")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c")))
  final val TADPOLE: List[QueryRelation] = List(
    QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "b")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("c", "a")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "e")))
  final val SPLIT: List[QueryRelation] = List(
    QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c", "d", "z", "y")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("c", "d", "e", "i", "j")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "d", "f", "g", "h")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("f", "g", "h", "k", "b")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("f", "g", "h", "n", "b")))
  final val BARBELL: List[QueryRelation] = List(
    QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "b")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "c")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("d", "e")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("e", "f")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("d", "f")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("c", "d"))
  )
  final val FFT: List[QueryRelation] = List(
    QueryRelationFactory.createQueryRelationWithNoSelects(List("y0", "y1")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("x0", "y0")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("x0", "y1")),
    QueryRelationFactory.createQueryRelationWithNoSelects(List("x1", "y0"))
  )
  final val solver = GHDSolver


  test("Can form 2 node AJAR GHD for length 2 path query") {
    val ajarGHDs = GHDSolver.computeAJAR_GHD(PATH2.toSet, Set("a", "c"))
    assert(ajarGHDs.size == 1) // there should only be one option
    val ajarGHD = ajarGHDs.head
    assertResult(Set("a", "b", "c"))(ajarGHD.attrSet)
    assert(ajarGHD.children.size == 0)
  }

  test("Check that we can reroot a GHD") {
    val decomp = new GHDNode(List(TADPOLE(0)))
    val decompChild1 = new GHDNode(List(TADPOLE(1), TADPOLE(2)))
    val decompChild2 = new GHDNode(List(TADPOLE(3)))
    decomp.children = List(decompChild1, decompChild2)

    GHDSolver.reroot(decomp, decompChild1)
    assert(decompChild1.children.size == 1)
    assertResult(List(TADPOLE(0)))(decompChild1.children.head.rels)
    assert(decompChild1.children.head.children.size == 1)
    assertResult(List(TADPOLE(3)))(decompChild1.children.head.children.head.rels)
  }

  test("Check that we can reroot a more complex tree (although this one isn't a valid GHD)") {
    val tree = new GHDNode(List(BARBELL(0)))
    val level1Child1 = new GHDNode(List(BARBELL(1)))
    val level1Child2 = new GHDNode(List(BARBELL(2)))
    tree.children = List(level1Child1, level1Child2)
    val level2Child1 = new GHDNode(List(BARBELL(3)))
    val level2Child2 = new GHDNode(List(BARBELL(4)))
    level1Child2.children = List(level2Child1, level2Child2)

    GHDSolver.reroot(tree, level1Child2)
    assert(level1Child2.children.size == 3)
    val childWithChild = level1Child2.children.find(child => child.children.size > 0)
    assert(childWithChild.isDefined)
    assertResult(List(BARBELL(0)))(childWithChild.get.rels)
    assert(childWithChild.get.children.size == 1)
    assertResult(List(BARBELL(1)))(childWithChild.get.children.head.rels)
  }

  test("Can form 3 node AJAR GHD for barbell") {
    val ajarGHDs = GHDSolver.computeAJAR_GHD(BARBELL.toSet, Set("c", "d"))

    val singleNodeG_0Trees = ajarGHDs.filter(ghd => {
      ghd.attrSet.equals(Set("c", "d")) &&
        ghd.children.size == 2 &&
        (ghd.children.head.attrSet.equals(Set("d", "e", "f")) &&
        ghd.children.tail.head.attrSet.equals(Set("a", "b", "c"))) ||
        (ghd.children.head.attrSet.equals(Set("a", "b", "c")) &&
          ghd.children.tail.head.attrSet.equals(Set("d", "e", "f")))
    })

    assert(singleNodeG_0Trees.filter(ghd => ghd.children.head.children.isEmpty && ghd.children.tail.head.children.isEmpty).size == 36)
  }

  test("Can delete imaginary edges") {
    // Should delete just the upper node
    val iAB_BC = new GHDNode(List(QueryRelationFactory.createImaginaryQueryRelationWithNoSelects(List("a", "b"))))
    iAB_BC.children = List(new GHDNode(List(QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c")))))
    val  justBC = GHDSolver.deleteImaginaryEdges(iAB_BC)
    assert(justBC.isDefined)
    assertResult(justBC.get.attrSet)(Set("b", "c"))
    assert(justBC.get.children.isEmpty)

    // Should delete just the lower node
    val AB_iBC = new GHDNode(List(QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "b"))))
    AB_iBC.children = List(new GHDNode(List(QueryRelationFactory.createImaginaryQueryRelationWithNoSelects(List("b", "c")))))
    val justAB = GHDSolver.deleteImaginaryEdges(AB_iBC)
    assert(justAB.isDefined)
    assertResult(justAB.get.attrSet)(Set("a", "b"))
    assert(justAB.get.children.isEmpty)

    // deletes the root, and puts the left chld in as the root
    val AB_2BC = new GHDNode(List(QueryRelationFactory.createImaginaryQueryRelationWithNoSelects(List("a", "b"))))
    AB_2BC.children = List(new GHDNode(List(QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c")))),
      new GHDNode(List(QueryRelationFactory.createQueryRelationWithNoSelects(List("b", "c")))))
    val BC_BC = GHDSolver.deleteImaginaryEdges(AB_2BC)
    assert(BC_BC.isDefined)
    assertResult(Set("b", "c"))(BC_BC.get.attrSet)
    assertResult(Set("b", "c"))(BC_BC.get.children.head.attrSet)

    // deletes a relation but doesn't modify the structure of the tree
    val ABC_CD = new GHDNode(List(
      QueryRelationFactory.createQueryRelationWithNoSelects(List("a", "b")),
      QueryRelationFactory.createImaginaryQueryRelationWithNoSelects(List("b", "c"))))
    ABC_CD.children = List(new GHDNode(List(QueryRelationFactory.createQueryRelationWithNoSelects(List("c", "d")))))
    val twoNodes = GHDSolver.deleteImaginaryEdges(ABC_CD)
    assert(twoNodes.isDefined)
    assertResult(twoNodes.get.toList.size)(2)
  }

  test("Can identify connected components of graph when removing the chosen hyper edge leaves 2 disconnected components") {
    val chosen = List(RELATIONS.head)
    val partitions = solver.getPartitions(
      RELATIONS.tail, chosen, Set(), solver.getAttrSet(chosen))
    assert(partitions.isDefined)
    assert(partitions.get.size == 2)

    val firstPart = partitions.get.head
    val secondPart = partitions.get.tail.head
    assert(firstPart.size == 2 && secondPart.size == 1)
    assert(secondPart.head == RELATIONS(1))
    assert(firstPart.head == RELATIONS(2))
    assert(firstPart.tail.head == RELATIONS(3))
  }

  test("Finds all possible decompositions of len 2 path query)") {
    val decompositions = solver.getDecompositions(PATH2).toSet[GHDNode]
    /**
     * The decompositions we expect are [ABC] and [AB]--[BC] and [BC]--[AB]
     */
    assert(decompositions.size == 3)
    val singleBag = new GHDNode(PATH2)
    val twoBagWithRootAB = new GHDNode(PATH2.take(1))
    twoBagWithRootAB.children = List(new GHDNode(PATH2.tail.take(1)))
    val twoBagWithRootBC = new GHDNode(PATH2.tail.take(1))
    twoBagWithRootBC.children = List(new GHDNode(PATH2.take(1)))
    assert(decompositions.contains(singleBag))
    assert(decompositions.contains(twoBagWithRootAB))
    assert(decompositions.contains(twoBagWithRootBC))
  }

  test("Decomps and scores triangle query correctly") {
    val decompositions = solver.getDecompositions(TADPOLE.take(3)) // drop the tail
    /**
     * The decompositions we expect are
     * [ABC]
     * [any one rel] -- [other two rels] (this can be inverted)
     */
    assert(decompositions.size == 7)
    val fractionalScores = decompositions.map((root: GHDNode) => root.fractionalScoreTree())
    assert(fractionalScores.min === 1.5)
  }

  test("Find max bag size 5 decomposition of query") {
    val decompositions2 = solver.getDecompositions(SPLIT)
    assert(!decompositions2.filter((root: GHDNode) => root.scoreTree <= 5).isEmpty)
  }

  test("Finds an expected decomp of the barbell query") {
    // make sure that we get partition correctly after we choose the root
    val chosen = List(BARBELL.last)
    val partitions = solver.getPartitions(
      BARBELL.take(6), chosen, Set(), solver.getAttrSet(chosen))
    assert(partitions.isDefined)
    assert(partitions.get.size == 2)

    // filtering to look for expectedDecomp with one edge as root, and two triangles as children
    val decompositions = solver.getDecompositions(BARBELL)
    var expectedDecomp = decompositions.filter((root : GHDNode) => root.rels.size == 1
      && root.rels.contains(BARBELL.last)
      && root.children.size == 2)
    expectedDecomp = expectedDecomp.filter((root : GHDNode) =>
      (root.children(0).attrSet.equals(Set("a", "b", "c")) && root.children(1).attrSet.equals(Set("d", "e", "f")))
        || root.children(1).attrSet.equals(Set("a", "b", "c")) && root.children(0).attrSet.equals(Set("d", "e", "f")))
    expectedDecomp = expectedDecomp.filter((root : GHDNode) => root.children(0).rels.size == 3 && root.children(1).rels.size == 3)

    assertResult(1)(expectedDecomp.size)
  }

  test("Finds all possible decompositions of tadpole query)") {
    val decompositions = solver.getDecompositions(TADPOLE)
    assert(decompositions.size == 21)
    assert(decompositions.filter((root: GHDNode) => root.rels.size == 1).size == 10)
    assert(decompositions.filter((root: GHDNode) => root.rels.size == 2).size == 6)
    assert(decompositions.filter((root: GHDNode) => root.rels.size == 3).size == 4)
    assert(decompositions.filter((root: GHDNode) => root.rels.size == 4).size == 1)
    val decompositionsSet = decompositions.toSet[GHDNode]
    /**
     * The decompositions we expect are
     * [AB]--[ABC] (*)
     *  |
     * [AE]
     *
     * root of above tree could also be AC
     *
     * [AB]--[ABCE]
     * [AC]--[ABCE]
     * [BC]--[ABCE]
     *
     * [ABC]--[AE]
     * [ABE]--[ABC] (*)
     * [ACE]--[ABC]
     * [AEBC]--[ABC]
     *
     * all of the above 2-node options also work if you switch the root and leaf
     *
     * [AE]--[AB]--[ABC]
     * [AE]--[ABC]--[AB]
     * [BC]--[ABC]--[AE]
     * [AE]--[ABC]--[BC]
     *
     * [ABCE] (*)
     *
     * Check that the ones marked (*) were found:
     */
    val decomp1 = new GHDNode(List(TADPOLE(0)))
    val decomp1Child1 = new GHDNode(List(TADPOLE(1), TADPOLE(2)))
    val decomp1Child2 = new GHDNode(List(TADPOLE(3)))
    decomp1.children = List(decomp1Child1, decomp1Child2)
    assert(decompositionsSet.contains(decomp1))

    val decomp2 = new GHDNode(List(TADPOLE(0), TADPOLE(3)))
    val decomp2Child = new GHDNode(List(TADPOLE(1), TADPOLE(2)))
    decomp2.children = List(decomp2Child)
    assert(decompositionsSet.contains(decomp2))

    val decomp3 = new GHDNode(List(TADPOLE(0), TADPOLE(1), TADPOLE(2), TADPOLE(3)))
    assert(decompositionsSet.contains(decomp3))

    // Also check that we found the lowest fhw option
    val decomp4 = new GHDNode(List(TADPOLE(3)))
    val decomp4Child = new GHDNode(List(TADPOLE(0), TADPOLE(1), TADPOLE(2)))
    decomp4.children = List(decomp4Child)
    assert(decompositionsSet.contains(decomp4))

    val fractionalScores = decompositions.map((root: GHDNode) => root.fractionalScoreTree())
    assert(fractionalScores.min === 1.5)
  }
}