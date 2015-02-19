package org.protege.editor.owl.ui.view;

import java.awt.BorderLayout;
import java.awt.Component;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JList;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.protege.editor.core.ui.list.MList;
import org.protege.editor.core.ui.list.MListItem;
import org.protege.editor.owl.model.classexpression.anonymouscls.AnonymousDefinedClassManager;
import org.protege.editor.owl.ui.renderer.OWLCellRenderer;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLObject;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.OWLEntityRemover;

/**
 * Author: drummond<br>
 * http://www.cs.man.ac.uk/~drummond/<br><br>
 * <p/>
 * The University Of Manchester<br>
 * Bio Health Informatics Group<br>
 * Date: Nov 24, 2008<br><br>
 */
public class AnonymousClassesView extends AbstractActiveOntologyViewComponent implements Deleteable, Copyable {

    /**
     * 
     */
    private static final long serialVersionUID = 6603279963895348251L;

    private MList list;

    private OWLEntityRemover remover;

    private java.util.List<ChangeListener> listeners = new ArrayList<ChangeListener>();


    protected void initialiseOntologyView() throws Exception {
        setLayout(new BorderLayout());

        list = new MList();
        final MList.MListCellRenderer ren = (MList.MListCellRenderer)list.getCellRenderer();
        ren.setContentRenderer(new OWLCellRenderer(getOWLEditorKit(), true, true){
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof AnonymousClassItem){
                    value = ((AnonymousClassItem)value).getOWLClass();
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });

        add(list, BorderLayout.CENTER);

        list.addListSelectionListener(new ListSelectionListener(){
            public void valueChanged(ListSelectionEvent event) {
                for (ChangeListener l : new ArrayList<ChangeListener>(listeners)){
                    l.stateChanged(new ChangeEvent(AnonymousClassesView.this));
                }
                Object item = list.getSelectedValue();
                if (item != null){
                    getOWLEditorKit().getOWLWorkspace().getOWLSelectionModel().setSelectedEntity(((AnonymousClassItem)item).getOWLClass());
                }
            }
        });


        remover = new OWLEntityRemover(getOWLModelManager().getOWLOntologyManager(),
                                       getOWLModelManager().getOntologies());
    }


    protected void disposeOntologyView() {
        // do nothing
    }


    protected void updateView(OWLOntology activeOntology) throws Exception {
        Set<AnonymousClassItem> clses = new HashSet<AnonymousClassItem>();
        AnonymousDefinedClassManager ADCManager = getOWLModelManager().get(AnonymousDefinedClassManager.ID);
        if (ADCManager != null){
            for (OWLClass cls : activeOntology.getClassesInSignature()){
                if (ADCManager.isAnonymous(cls)){
                    clses.add(new AnonymousClassItem(cls));
                }
            }
        }
        list.setListData(clses.toArray());
    }


    public boolean canDelete() {
        return list.getSelectedIndex() >= 0;
    }


    public void handleDelete() {
        remover.reset();
        for (Object clsItem : list.getSelectedValues()){
            ((AnonymousClassItem)clsItem).getOWLClass().accept(remover);
        }
        getOWLModelManager().applyChanges(remover.getChanges());
    }


    public boolean canCopy() {
        return list.getSelectedIndex() >= 0;
    }


    public java.util.List<OWLObject> getObjectsToCopy() {
        List<OWLObject> sel = new ArrayList<OWLObject>();
for (Object clsItem : list.getSelectedValues()){
            sel.add(((AnonymousClassItem)clsItem).getOWLClass());
        }
        return sel;
    }


    public void addChangeListener(ChangeListener listener) {
        listeners.add(listener);
    }


    public void removeChangeListener(ChangeListener listener) {
        listeners.remove(listener);
    }


    private class AnonymousClassItem implements MListItem {

        private OWLClass cls;


        public AnonymousClassItem(OWLClass cls) {
            this.cls = cls;
        }


        public boolean isEditable() {
            return false;
        }


        public void handleEdit() {
            // do nothing
        }


        public boolean isDeleteable() {
            return true;
        }


        public boolean handleDelete() {
            remover.reset();
            cls.accept(remover);
            getOWLModelManager().applyChanges(remover.getChanges());
            return true;
        }


        public String getTooltip() {
            return "";
        }


        public OWLClass getOWLClass() {
            return cls;
        }
    }
}
