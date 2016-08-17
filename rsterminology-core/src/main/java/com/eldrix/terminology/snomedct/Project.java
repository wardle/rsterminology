package com.eldrix.terminology.snomedct;

import java.util.stream.Stream;

import com.eldrix.terminology.snomedct.auto._Project;

public class Project extends _Project {
    private static final long serialVersionUID = 1L; 

    /**
     * Return a stream containing this project and its parents
     * @return
     */
    public Stream<Project> getOrderedParents() {
    	return _addParentsToStream(Stream.builder()).build();
    }

    private Stream.Builder<Project> _addParentsToStream(Stream.Builder<Project> sb) {
    	sb.accept(this);
    	final Project parent = getParent();
    	return  parent != null ? parent._addParentsToStream(sb) : sb;
    }
}
