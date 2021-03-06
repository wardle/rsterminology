package com.eldrix.terminology.snomedct.auto;

import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.exp.Property;

import com.eldrix.terminology.snomedct.Concept;
import com.eldrix.terminology.snomedct.Project;

/**
 * Class _ProjectConcept was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
public abstract class _ProjectConcept extends CayenneDataObject {

    private static final long serialVersionUID = 1L; 

    public static final String CONCEPTCONCEPTID_PK_COLUMN = "conceptconceptid";
    public static final String PROJECTID_PK_COLUMN = "projectid";

    public static final Property<Concept> CONCEPT = new Property<Concept>("concept");
    public static final Property<Project> PROJECT = new Property<Project>("project");

    public void setConcept(Concept concept) {
        setToOneTarget("concept", concept, true);
    }

    public Concept getConcept() {
        return (Concept)readProperty("concept");
    }


    public void setProject(Project project) {
        setToOneTarget("project", project, true);
    }

    public Project getProject() {
        return (Project)readProperty("project");
    }


}
