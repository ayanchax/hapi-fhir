package ca.uhn.fhir.context;

import static org.apache.commons.lang3.StringUtils.*;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import ca.uhn.fhir.model.api.CodeableConceptElement;
import ca.uhn.fhir.model.api.ICodeEnum;
import ca.uhn.fhir.model.api.ICompositeDatatype;
import ca.uhn.fhir.model.api.ICompositeElement;
import ca.uhn.fhir.model.api.IDatatype;
import ca.uhn.fhir.model.api.IElement;
import ca.uhn.fhir.model.api.IPrimitiveDatatype;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.IResourceBlock;
import ca.uhn.fhir.model.api.ResourceReference;
import ca.uhn.fhir.model.api.annotation.Block;
import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.ChildResource;
import ca.uhn.fhir.model.api.annotation.Choice;
import ca.uhn.fhir.model.api.annotation.CodeTableDef;
import ca.uhn.fhir.model.api.annotation.DatatypeDef;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.Narrative;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import ca.uhn.fhir.model.dstu.composite.NarrativeDt;
import ca.uhn.fhir.model.primitive.CodeDt;
import ca.uhn.fhir.model.primitive.DateDt;
import ca.uhn.fhir.model.primitive.ICodedDatatype;
import ca.uhn.fhir.model.primitive.XhtmlDt;

class ModelScanner {
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ModelScanner.class);

	private Map<Class<? extends IElement>, BaseRuntimeElementDefinition<?>> myClassToElementDefinitions = new HashMap<Class<? extends IElement>, BaseRuntimeElementDefinition<?>>();
	private Map<String, RuntimeResourceDefinition> myNameToResourceDefinitions = new HashMap<String, RuntimeResourceDefinition>();
	private Set<Class<? extends IElement>> myScanAlso = new HashSet<Class<? extends IElement>>();

	// private Map<String, RuntimeResourceDefinition>
	// myNameToDatatypeDefinitions = new HashMap<String,
	// RuntimeDatatypeDefinition>();

	private Set<Class<? extends ICodeEnum>> myScanAlsoCodeTable = new HashSet<Class<? extends ICodeEnum>>();

	private RuntimeChildUndeclaredExtensionDefinition myRuntimeChildUndeclaredExtensionDefinition;

	ModelScanner(Class<? extends IResource>... theResourceTypes) throws ConfigurationException {

		Set<Class<? extends IElement>> toScan = new HashSet<Class<? extends IElement>>(Arrays.asList(theResourceTypes));
		toScan.add(NarrativeDt.class);
		toScan.add(DateDt.class);
		toScan.add(CodeDt.class);

		do {
			for (Class<? extends IElement> nextClass : toScan) {
				scan(nextClass);
			}
			for (Iterator<Class<? extends IElement>> iter = myScanAlso.iterator(); iter.hasNext();) {
				if (myClassToElementDefinitions.containsKey(iter.next())) {
					iter.remove();
				}
			}
			toScan.clear();
			toScan.addAll(myScanAlso);
			myScanAlso.clear();
		} while (!toScan.isEmpty());

		for (BaseRuntimeElementDefinition<?> next : myClassToElementDefinitions.values()) {
			next.sealAndInitialize(myClassToElementDefinitions);
		}

		myRuntimeChildUndeclaredExtensionDefinition = new RuntimeChildUndeclaredExtensionDefinition();
		myRuntimeChildUndeclaredExtensionDefinition.sealAndInitialize(myClassToElementDefinitions);
		
		ourLog.info("Done scanning FHIR library, found {} model entries", myClassToElementDefinitions.size());

	}

	public RuntimeChildUndeclaredExtensionDefinition getRuntimeChildUndeclaredExtensionDefinition() {
		return myRuntimeChildUndeclaredExtensionDefinition;
	}

	public Map<Class<? extends IElement>, BaseRuntimeElementDefinition<?>> getClassToElementDefinitions() {
		return myClassToElementDefinitions;
	}

	public Map<String, RuntimeResourceDefinition> getNameToResourceDefinitions() {
		return (myNameToResourceDefinitions);
	}

	private void addScanAlso(Class<? extends IElement> theType) {
		if (theType.isInterface()) {
			return;
		}
		myScanAlso.add(theType);
	}

	private void scan(Class<? extends IElement> theClass) throws ConfigurationException {
		BaseRuntimeElementDefinition<?> existingDef = myClassToElementDefinitions.get(theClass);
		if (existingDef != null) {
			return;
		}

		ResourceDef resourceDefinition = theClass.getAnnotation(ResourceDef.class);
		if (resourceDefinition != null) {
			if (!IResource.class.isAssignableFrom(theClass)) {
				throw new ConfigurationException("Resource type contains a @" + ResourceDef.class.getSimpleName() + " annotation but does not implement " + IResource.class.getCanonicalName() + ": " + theClass.getCanonicalName());
			}
			@SuppressWarnings("unchecked")
			Class<? extends IResource> resClass = (Class<? extends IResource>) theClass;
			scanResource(resClass, resourceDefinition);
		}

		DatatypeDef datatypeDefinition = theClass.getAnnotation(DatatypeDef.class);
		if (datatypeDefinition != null) {
			if (ICompositeDatatype.class.isAssignableFrom(theClass)) {
				@SuppressWarnings("unchecked")
				Class<? extends ICompositeDatatype> resClass = (Class<? extends ICompositeDatatype>) theClass;
				scanCompositeDatatype(resClass, datatypeDefinition);
			} else if (IPrimitiveDatatype.class.isAssignableFrom(theClass)) {
				@SuppressWarnings({ "unchecked" })
				Class<? extends IPrimitiveDatatype<?>> resClass = (Class<? extends IPrimitiveDatatype<?>>) theClass;
				scanPrimitiveDatatype(resClass, datatypeDefinition);
			} else {
				throw new ConfigurationException("Resource type contains a @" + DatatypeDef.class.getSimpleName() + " annotation but does not implement " + IDatatype.class.getCanonicalName() + ": " + theClass.getCanonicalName());
			}
		}

		CodeTableDef codeTableDefinition = theClass.getAnnotation(CodeTableDef.class);
		if (codeTableDefinition != null) {
			if (ICodeEnum.class.isAssignableFrom(theClass)) {
				@SuppressWarnings("unchecked")
				Class<? extends ICodeEnum> resClass = (Class<? extends ICodeEnum>) theClass;
				scanCodeTable(resClass, codeTableDefinition);
			} else {
				throw new ConfigurationException("Type contains a @" + CodeTableDef.class.getSimpleName() + " annotation but does not implement " + ICodeEnum.class.getCanonicalName() + ": " + theClass.getCanonicalName());
			}
		}

		Block blockDefinition = theClass.getAnnotation(Block.class);
		if (blockDefinition != null) {
			if (IResourceBlock.class.isAssignableFrom(theClass)) {
				@SuppressWarnings("unchecked")
				Class<? extends IResourceBlock> blockClass = (Class<? extends IResourceBlock>) theClass;
				scanBlock(blockClass, blockDefinition);
			} else {
				throw new ConfigurationException("Type contains a @" + Block.class.getSimpleName() + " annotation but does not implement " + IResourceBlock.class.getCanonicalName() + ": " + theClass.getCanonicalName());
			}
		}

		if (blockDefinition == null && codeTableDefinition == null && datatypeDefinition == null && resourceDefinition == null) {
			throw new ConfigurationException("Resource type does not contain any valid HAPI-FHIR annotations: " + theClass.getCanonicalName());
		}
	}


	private void scanBlock(Class<? extends IResourceBlock> theClass, Block theBlockDefinition) {
		ourLog.debug("Scanning resource block class: {}", theClass.getName());

		String resourceName = theBlockDefinition.name();
		if (isBlank(resourceName)) {
			throw new ConfigurationException("Block type @" + Block.class.getSimpleName() + " annotation contains no name: " + theClass.getCanonicalName());
		}

		RuntimeResourceBlockDefinition resourceDef = new RuntimeResourceBlockDefinition(resourceName, theClass);
		myClassToElementDefinitions.put(theClass, resourceDef);

		scanCompositeElementForChildren(theClass, resourceDef, null);
	}

	private String scanCodeTable(Class<? extends ICodeEnum> theCodeType, CodeTableDef theCodeTableDefinition) {
		return null; // TODO: implement
	}

	private void scanCompositeDatatype(Class<? extends ICompositeDatatype> theClass, DatatypeDef theDatatypeDefinition) {
		ourLog.debug("Scanning resource class: {}", theClass.getName());

		String resourceName = theDatatypeDefinition.name();
		if (isBlank(resourceName)) {
			throw new ConfigurationException("Resource type @" + ResourceDef.class.getSimpleName() + " annotation contains no resource name: " + theClass.getCanonicalName());
		}

		RuntimeCompositeDatatypeDefinition resourceDef = new RuntimeCompositeDatatypeDefinition(resourceName, theClass);
		myClassToElementDefinitions.put(theClass, resourceDef);
		scanCompositeElementForChildren(theClass, resourceDef, null);
	}

	@SuppressWarnings("unchecked")
	private void scanCompositeElementForChildren(Class<? extends ICompositeElement> theClass, BaseRuntimeElementCompositeDefinition<?> theDefinition, Integer theIdentifierOrder) {
		Set<String> elementNames = new HashSet<String>();
		TreeMap<Integer, BaseRuntimeDeclaredChildDefinition> orderToElementDef = new TreeMap<Integer, BaseRuntimeDeclaredChildDefinition>();
		TreeMap<Integer, BaseRuntimeDeclaredChildDefinition> orderToExtensionDef = new TreeMap<Integer, BaseRuntimeDeclaredChildDefinition>();

		LinkedList<Class<? extends ICompositeElement>> classes = new LinkedList<Class<? extends ICompositeElement>>();
		Class<? extends ICompositeElement> current = theClass;
		do {
			classes.push(current);
			if (ICompositeElement.class.isAssignableFrom(current.getSuperclass())) {
				current = (Class<? extends ICompositeElement>) current.getSuperclass();
			} else {
				current = null;
			}
		} while (current != null);

		for (Class<? extends ICompositeElement> next : classes) {
			scanCompositeElementForChildren(next, theDefinition, elementNames, orderToElementDef, orderToExtensionDef);
		}

		while (orderToElementDef.size() > 0 && orderToElementDef.firstKey() < 0) {
			BaseRuntimeDeclaredChildDefinition elementDef = orderToElementDef.remove(orderToElementDef.firstKey());
			if (elementDef.getElementName().equals("identifier")) {
				orderToElementDef.put(theIdentifierOrder, elementDef);
			} else {
				throw new ConfigurationException("Don't know how to handle element: " + elementDef.getElementName());
			}
		}

		TreeSet<Integer> orders = new TreeSet<Integer>();
		orders.addAll(orderToElementDef.keySet());
		orders.addAll(orderToExtensionDef.keySet());
		
		for (Integer i : orders) {
			BaseRuntimeChildDefinition nextChild = orderToElementDef.get(i);
			if (nextChild != null) {
				theDefinition.addChild(nextChild);
			}
			BaseRuntimeDeclaredChildDefinition nextExt = orderToExtensionDef.get(i);
			if (nextExt != null) {
				theDefinition.addExtension((RuntimeChildDeclaredExtensionDefinition)nextExt);
			}
		}

	}

	@SuppressWarnings("unchecked")
	private void scanCompositeElementForChildren(Class<? extends ICompositeElement> theClass, BaseRuntimeElementCompositeDefinition<?> theDefinition, Set<String> elementNames, TreeMap<Integer, BaseRuntimeDeclaredChildDefinition> theOrderToElementDef, TreeMap<Integer,BaseRuntimeDeclaredChildDefinition> theOrderToExtensionDef) {
		for (Field next : theClass.getDeclaredFields()) {

			Narrative hasNarrative = next.getAnnotation(Narrative.class);
			if (hasNarrative != null) {
				RuntimeChildNarrativeDefinition def;
				try {
					def = new RuntimeChildNarrativeDefinition(next, hasNarrative.name());
				} catch (Exception e) {
					throw new ConfigurationException("Failed to find narrative field", e);
				}
				theDefinition.addChild(def);
				continue;
			}

			Child element = next.getAnnotation(Child.class);
			if (element == null) {
				ourLog.debug("Ignoring non-type field '" + next.getName() + "' on target type: " + theClass);
				continue;
			}

			String elementName = element.name();
			int order = element.order();
			int min = element.min();
			int max = element.max();
			TreeMap<Integer, BaseRuntimeDeclaredChildDefinition> orderMap = theOrderToElementDef;
			
			Extension extensionAttr = next.getAnnotation(Extension.class);
			if (extensionAttr != null) {
				orderMap = theOrderToExtensionDef;
			}
			
			/*
			 * Anything that's marked as unknown is given a new ID that is <0 so
			 * that it doesn't conflict wityh any given IDs and can be figured
			 * out later
			 */
			while (order == Child.ORDER_UNKNOWN && orderMap.containsKey(order)) {
				order--;
			}

			Choice choiceAttr = element.choice();
			List<Class<? extends IElement>> choiceTypes = new ArrayList<Class<? extends IElement>>();
			for (Class<? extends IElement> nextChoiceType : choiceAttr.types()) {
				choiceTypes.add(nextChoiceType);
			}

			if (orderMap.containsKey(order)) {
				throw new ConfigurationException("Detected duplicate field order '" + order + "' for element named '" + elementName + "' in type '" + theClass.getCanonicalName() + "'");
			}

			if (elementNames.contains(elementName)) {
				throw new ConfigurationException("Detected duplicate field name '" + elementName + "' in type '" + theClass.getCanonicalName() + "'");
			}

			Class<?> nextElementType = determineElementType(next);

			ChildResource resRefAnnotation = next.getAnnotation(ChildResource.class);
			if (choiceTypes.isEmpty() == false) {
				/*
				 * Child is a choice element
				 */
				for (Class<? extends IElement> nextType : choiceTypes) {
					addScanAlso(nextType);
				}
				RuntimeChildChoiceDefinition def = new RuntimeChildChoiceDefinition(next, elementName, min, max, choiceTypes);
				orderMap.put(order, def);

			} else if (extensionAttr != null) {
				/*
				 * Child is an extension
				 */
				Class<? extends IElement> et = (Class<? extends IElement>) nextElementType;
				RuntimeChildDeclaredExtensionDefinition def = new RuntimeChildDeclaredExtensionDefinition(next, min, max, elementName, extensionAttr.url(), et);
				orderMap.put(order, def);
				if (IElement.class.isAssignableFrom(nextElementType )) {
					addScanAlso((Class<? extends IElement>) nextElementType);
				}
			} else if (ResourceReference.class.isAssignableFrom(nextElementType)) {
				/*
				 * Child is a resource reference
				 */
				if (resRefAnnotation == null) {
					throw new ConfigurationException("Field '" + elementName + "' in type '" + theClass.getCanonicalName() + "' is a resource reference but does not have a @" + ChildResource.class.getSimpleName() + " annotation");
				}

				Class<? extends IResource>[] refType = resRefAnnotation.types();
				List<Class<? extends IResource>> refTypesList = Arrays.asList(refType);
				for (Class<? extends IElement> nextType : refTypesList) {
					addScanAlso(nextType);
				}
				RuntimeChildResourceDefinition def = new RuntimeChildResourceDefinition(next, elementName, min, max, refTypesList);
				orderMap.put(order, def);

			} else if (IResourceBlock.class.isAssignableFrom(nextElementType)) {
				/*
				 * Child is a resource block (i.e. a sub-tag within a resource)
				 * TODO: do these have a better name according to HL7?
				 */

				Class<? extends IResourceBlock> blockDef = (Class<? extends IResourceBlock>) nextElementType;
				addScanAlso(blockDef);
				RuntimeChildResourceBlockDefinition def = new RuntimeChildResourceBlockDefinition(next, min, max, elementName, blockDef);
				orderMap.put(order, def);

			} else if (IDatatype.class.isAssignableFrom(nextElementType)) {
				Class<? extends IDatatype> nextDatatype = (Class<? extends IDatatype>) nextElementType;

				addScanAlso(nextDatatype);
				BaseRuntimeChildDatatypeDefinition def;
				if (IPrimitiveDatatype.class.isAssignableFrom(nextElementType)) {
					def = new RuntimeChildPrimitiveDatatypeDefinition(next, elementName, min, max, nextDatatype);
				} else {
					def = new RuntimeChildCompositeDatatypeDefinition(next, elementName, min, max, nextDatatype);
				}

				CodeableConceptElement concept = next.getAnnotation(CodeableConceptElement.class);
				if (concept != null) {
					if (!ICodedDatatype.class.isAssignableFrom(nextDatatype)) {
						throw new ConfigurationException("Field '" + elementName + "' in type '" + theClass.getCanonicalName() + "' is marked as @" + CodeableConceptElement.class.getCanonicalName() + " but type is not a subtype of " + ICodedDatatype.class.getName());
					} else {
						Class<? extends ICodeEnum> type = concept.type();
						myScanAlsoCodeTable.add(type);
						def.setCodeType(type);
					}
				}

				orderMap.put(order, def);

				// TODO: handle codes
			} else {
				throw new ConfigurationException("Field '" + elementName + "' in type '" + theClass.getCanonicalName() + "' is not a valid child type");
			}

			elementNames.add(elementName);
		}
	}

	private Class<?> determineElementType(Field next) {
		Class<?> nextElementType = next.getType();
		if (List.class.equals(nextElementType)) {
			nextElementType = getGenericCollectionTypeOfField(next);
		} else if (Collection.class.isAssignableFrom(nextElementType)) {
			throw new ConfigurationException("Field '" + next.getName() + "' in type '" + next.getClass().getCanonicalName() + "' is a Collection - Only java.util.List curently supported");
		}
		return nextElementType;
	}

	private String scanPrimitiveDatatype(Class<? extends IPrimitiveDatatype<?>> theClass, DatatypeDef theDatatypeDefinition) {
		ourLog.debug("Scanning resource class: {}", theClass.getName());

		String resourceName = theDatatypeDefinition.name();
		if (isBlank(resourceName)) {
			throw new ConfigurationException("Resource type @" + ResourceDef.class.getSimpleName() + " annotation contains no resource name: " + theClass.getCanonicalName());
		}

		BaseRuntimeElementDefinition<?> resourceDef;
		if (theClass.equals(XhtmlDt.class)) {
			@SuppressWarnings("unchecked")
			Class<XhtmlDt> clazz = (Class<XhtmlDt>) theClass;
			resourceDef = new RuntimePrimitiveDatatypeNarrativeDefinition(resourceName, clazz);
		} else {
			resourceDef = new RuntimePrimitiveDatatypeDefinition(resourceName, theClass);
		}
		myClassToElementDefinitions.put(theClass, resourceDef);

		return resourceName;
	}

	private String scanResource(Class<? extends IResource> theClass, ResourceDef resourceDefinition) {
		ourLog.debug("Scanning resource class: {}", theClass.getName());

		String resourceName = resourceDefinition.name();
		if (isBlank(resourceName)) {
			throw new ConfigurationException("Resource type @" + ResourceDef.class.getSimpleName() + " annotation contains no resource name: " + theClass.getCanonicalName());
		}

		if (myNameToResourceDefinitions.containsKey(resourceName)) {
			if (!myNameToResourceDefinitions.get(resourceName).getImplementingClass().equals(theClass)) {
				throw new ConfigurationException("Detected duplicate element name '" + resourceName + "' in types '" + theClass.getCanonicalName() + "' and '" + myNameToResourceDefinitions.get(resourceName).getImplementingClass() + "'");
			}
			return resourceName;
		}

		RuntimeResourceDefinition resourceDef = new RuntimeResourceDefinition(theClass, resourceName);
		myClassToElementDefinitions.put(theClass, resourceDef);
		myNameToResourceDefinitions.put(resourceName, resourceDef);

		scanCompositeElementForChildren(theClass, resourceDef, resourceDefinition.identifierOrder());

		return resourceName;
	}

	private static Class<?> getGenericCollectionTypeOfField(Field next) {
		// Type genericSuperclass = next.getType().getGenericSuperclass();
		Class<?> type;
		// if (genericSuperclass == null) {
		ParameterizedType collectionType = (ParameterizedType) next.getGenericType();
		type = (Class<?>) collectionType.getActualTypeArguments()[0];
		// }else {
		// Type[] actualTypeArguments = ((ParameterizedType)
		// genericSuperclass).getActualTypeArguments();
		// type = (Class<?>) actualTypeArguments[0];
		// }
		return type;
	}

}
