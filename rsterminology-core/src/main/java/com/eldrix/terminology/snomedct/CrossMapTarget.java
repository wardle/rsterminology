package com.eldrix.terminology.snomedct;


import com.eldrix.terminology.snomedct.auto._CrossMapTarget;

public class CrossMapTarget extends _CrossMapTarget {
    private static final long serialVersionUID = 1L; 
    private static final String NOT_IN_SCOPE_CODE="#NIS";

    public boolean isNotInScope() {
    	return NOT_IN_SCOPE_CODE.equals(getCodes());
    }
}
