package com.eldrix.terminology.snomedct;

import java.util.Optional;

import com.eldrix.terminology.snomedct.Semantic.RelationType;
import com.eldrix.terminology.snomedct.auto._Relationship;

public class Relationship extends _Relationship {
    private static final long serialVersionUID = 1L; 
	
	public Optional<RelationType> getRelationType() {
		return Optional.ofNullable(RelationType.relationTypeForConceptId(getRelationshipTypeConcept().getConceptId()));
	}
    
}
