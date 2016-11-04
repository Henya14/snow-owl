/**
 */
package com.b2international.snowowl.snomed.ecl.ecl.impl;

import com.b2international.snowowl.snomed.ecl.ecl.Any;
import com.b2international.snowowl.snomed.ecl.ecl.ConceptReference;
import com.b2international.snowowl.snomed.ecl.ecl.DescendantOf;
import com.b2international.snowowl.snomed.ecl.ecl.DescendantOrSelfOf;
import com.b2international.snowowl.snomed.ecl.ecl.EclFactory;
import com.b2international.snowowl.snomed.ecl.ecl.EclPackage;
import com.b2international.snowowl.snomed.ecl.ecl.ExpressionConstraint;
import com.b2international.snowowl.snomed.ecl.ecl.MemberOf;
import com.b2international.snowowl.snomed.ecl.ecl.NestableExpression;
import com.b2international.snowowl.snomed.ecl.ecl.SimpleExpressionConstraint;

import org.eclipse.emf.ecore.EAttribute;
import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EReference;

import org.eclipse.emf.ecore.impl.EPackageImpl;

/**
 * <!-- begin-user-doc -->
 * An implementation of the model <b>Package</b>.
 * <!-- end-user-doc -->
 * @generated
 */
public class EclPackageImpl extends EPackageImpl implements EclPackage
{
  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass expressionConstraintEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass simpleExpressionConstraintEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass descendantOfEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass descendantOrSelfOfEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass memberOfEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass conceptReferenceEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass anyEClass = null;

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private EClass nestableExpressionEClass = null;

  /**
   * Creates an instance of the model <b>Package</b>, registered with
   * {@link org.eclipse.emf.ecore.EPackage.Registry EPackage.Registry} by the package
   * package URI value.
   * <p>Note: the correct way to create the package is via the static
   * factory method {@link #init init()}, which also performs
   * initialization of the package, or returns the registered package,
   * if one already exists.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @see org.eclipse.emf.ecore.EPackage.Registry
   * @see com.b2international.snowowl.snomed.ecl.ecl.EclPackage#eNS_URI
   * @see #init()
   * @generated
   */
  private EclPackageImpl()
  {
    super(eNS_URI, EclFactory.eINSTANCE);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private static boolean isInited = false;

  /**
   * Creates, registers, and initializes the <b>Package</b> for this model, and for any others upon which it depends.
   * 
   * <p>This method is used to initialize {@link EclPackage#eINSTANCE} when that field is accessed.
   * Clients should not invoke it directly. Instead, they should simply access that field to obtain the package.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @see #eNS_URI
   * @see #createPackageContents()
   * @see #initializePackageContents()
   * @generated
   */
  public static EclPackage init()
  {
    if (isInited) return (EclPackage)EPackage.Registry.INSTANCE.getEPackage(EclPackage.eNS_URI);

    // Obtain or create and register package
    EclPackageImpl theEclPackage = (EclPackageImpl)(EPackage.Registry.INSTANCE.get(eNS_URI) instanceof EclPackageImpl ? EPackage.Registry.INSTANCE.get(eNS_URI) : new EclPackageImpl());

    isInited = true;

    // Create package meta-data objects
    theEclPackage.createPackageContents();

    // Initialize created meta-data
    theEclPackage.initializePackageContents();

    // Mark meta-data to indicate it can't be changed
    theEclPackage.freeze();

  
    // Update the registry and return the package
    EPackage.Registry.INSTANCE.put(EclPackage.eNS_URI, theEclPackage);
    return theEclPackage;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getExpressionConstraint()
  {
    return expressionConstraintEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getSimpleExpressionConstraint()
  {
    return simpleExpressionConstraintEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getDescendantOf()
  {
    return descendantOfEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EReference getDescendantOf_Constraint()
  {
    return (EReference)descendantOfEClass.getEStructuralFeatures().get(0);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getDescendantOrSelfOf()
  {
    return descendantOrSelfOfEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EReference getDescendantOrSelfOf_Constraint()
  {
    return (EReference)descendantOrSelfOfEClass.getEStructuralFeatures().get(0);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getMemberOf()
  {
    return memberOfEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EReference getMemberOf_Constraint()
  {
    return (EReference)memberOfEClass.getEStructuralFeatures().get(0);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getConceptReference()
  {
    return conceptReferenceEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EAttribute getConceptReference_Id()
  {
    return (EAttribute)conceptReferenceEClass.getEStructuralFeatures().get(0);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EAttribute getConceptReference_Term()
  {
    return (EAttribute)conceptReferenceEClass.getEStructuralFeatures().get(1);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getAny()
  {
    return anyEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EClass getNestableExpression()
  {
    return nestableExpressionEClass;
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public EclFactory getEclFactory()
  {
    return (EclFactory)getEFactoryInstance();
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private boolean isCreated = false;

  /**
   * Creates the meta-model objects for the package.  This method is
   * guarded to have no affect on any invocation but its first.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public void createPackageContents()
  {
    if (isCreated) return;
    isCreated = true;

    // Create classes and their features
    expressionConstraintEClass = createEClass(EXPRESSION_CONSTRAINT);

    simpleExpressionConstraintEClass = createEClass(SIMPLE_EXPRESSION_CONSTRAINT);

    descendantOfEClass = createEClass(DESCENDANT_OF);
    createEReference(descendantOfEClass, DESCENDANT_OF__CONSTRAINT);

    descendantOrSelfOfEClass = createEClass(DESCENDANT_OR_SELF_OF);
    createEReference(descendantOrSelfOfEClass, DESCENDANT_OR_SELF_OF__CONSTRAINT);

    memberOfEClass = createEClass(MEMBER_OF);
    createEReference(memberOfEClass, MEMBER_OF__CONSTRAINT);

    conceptReferenceEClass = createEClass(CONCEPT_REFERENCE);
    createEAttribute(conceptReferenceEClass, CONCEPT_REFERENCE__ID);
    createEAttribute(conceptReferenceEClass, CONCEPT_REFERENCE__TERM);

    anyEClass = createEClass(ANY);

    nestableExpressionEClass = createEClass(NESTABLE_EXPRESSION);
  }

  /**
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  private boolean isInitialized = false;

  /**
   * Complete the initialization of the package and its meta-model.  This
   * method is guarded to have no affect on any invocation but its first.
   * <!-- begin-user-doc -->
   * <!-- end-user-doc -->
   * @generated
   */
  public void initializePackageContents()
  {
    if (isInitialized) return;
    isInitialized = true;

    // Initialize package
    setName(eNAME);
    setNsPrefix(eNS_PREFIX);
    setNsURI(eNS_URI);

    // Create type parameters

    // Set bounds for type parameters

    // Add supertypes to classes
    expressionConstraintEClass.getESuperTypes().add(this.getNestableExpression());
    simpleExpressionConstraintEClass.getESuperTypes().add(this.getExpressionConstraint());
    descendantOfEClass.getESuperTypes().add(this.getSimpleExpressionConstraint());
    descendantOrSelfOfEClass.getESuperTypes().add(this.getSimpleExpressionConstraint());
    memberOfEClass.getESuperTypes().add(this.getSimpleExpressionConstraint());
    conceptReferenceEClass.getESuperTypes().add(this.getSimpleExpressionConstraint());
    anyEClass.getESuperTypes().add(this.getSimpleExpressionConstraint());

    // Initialize classes and features; add operations and parameters
    initEClass(expressionConstraintEClass, ExpressionConstraint.class, "ExpressionConstraint", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);

    initEClass(simpleExpressionConstraintEClass, SimpleExpressionConstraint.class, "SimpleExpressionConstraint", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);

    initEClass(descendantOfEClass, DescendantOf.class, "DescendantOf", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
    initEReference(getDescendantOf_Constraint(), this.getNestableExpression(), null, "constraint", null, 0, 1, DescendantOf.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

    initEClass(descendantOrSelfOfEClass, DescendantOrSelfOf.class, "DescendantOrSelfOf", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
    initEReference(getDescendantOrSelfOf_Constraint(), this.getNestableExpression(), null, "constraint", null, 0, 1, DescendantOrSelfOf.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

    initEClass(memberOfEClass, MemberOf.class, "MemberOf", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
    initEReference(getMemberOf_Constraint(), this.getSimpleExpressionConstraint(), null, "constraint", null, 0, 1, MemberOf.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, IS_COMPOSITE, !IS_RESOLVE_PROXIES, !IS_UNSETTABLE, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

    initEClass(conceptReferenceEClass, ConceptReference.class, "ConceptReference", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);
    initEAttribute(getConceptReference_Id(), ecorePackage.getEString(), "id", null, 0, 1, ConceptReference.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);
    initEAttribute(getConceptReference_Term(), ecorePackage.getEString(), "term", null, 0, 1, ConceptReference.class, !IS_TRANSIENT, !IS_VOLATILE, IS_CHANGEABLE, !IS_UNSETTABLE, !IS_ID, IS_UNIQUE, !IS_DERIVED, IS_ORDERED);

    initEClass(anyEClass, Any.class, "Any", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);

    initEClass(nestableExpressionEClass, NestableExpression.class, "NestableExpression", !IS_ABSTRACT, !IS_INTERFACE, IS_GENERATED_INSTANCE_CLASS);

    // Create resource
    createResource(eNS_URI);
  }

} //EclPackageImpl
