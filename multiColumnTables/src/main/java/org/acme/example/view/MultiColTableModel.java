package org.acme.example.view;

import gov.nasa.arc.mct.components.AbstractComponent;
import gov.nasa.arc.mct.components.FeedProvider;
import gov.nasa.arc.mct.components.Placeholder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

@SuppressWarnings("serial")
class MultiColTableModel extends AbstractTableModel {
	//temp:
	private static final String UNIT_BASE = "deq";
	private static final String FSW_BASE = "ADC RPAM A REU PRT";
	private static final String TITLE_BASE = "THRM-T-BITBOX";
	private static final String ID_BASE = "ABCD-000";

	//private String[] columnNames; //deprecated
	private ArrayList<ColumnType> columnList;
	private AbstractComponent selectedComponent; //is this name confusing because it's the same as in MultiColView?
	private List<AbstractComponent> childrenList;
	private MultiColView multiColViewManifestation;
	private MultiColTable table;
	private ViewSettings settings;
	private Map<String, Object> values = new HashMap<String, Object>(); 
	private Map<String,List<Integer>> componentLocations;
	
	private ListenerManager listenerManager = new ListenerManager();
	
	public MultiColTableModel(AbstractComponent componentin, MultiColTable table, ViewSettings settings) {
		this.table = table;
		this.settings = settings;
		columnList = settings.getColumnTypes();
		multiColViewManifestation = table.getMultiColView();
		selectedComponent = componentin;
		childrenList = selectedComponent.getComponents();
		if(childrenList.size()==0) {
			childrenList = new ArrayList<AbstractComponent>();
			childrenList.add(selectedComponent);
		}
		//We clean out any components without feed providers
		List<AbstractComponent> tempList = new ArrayList<AbstractComponent>();
		for(AbstractComponent component : childrenList) {
			if(multiColViewManifestation.getFeedProvider(component)!=null) {
				tempList.add(component);
			}
		}
		childrenList = tempList;
		
		updateLocations();
	}
	
	@Override
	public int getColumnCount() { return settings.getNumberOfColumns(); } //inaccurate?
	public int getRowCount()    { return childrenList.size(); }
	public MultiColTable getMultiColTable() { return table; }
	public JTable getJTable() { return table.getTable(); }
	
	public AbstractComponent getComponentOfRow(int rowIndex) {
		return childrenList.get(rowIndex);
	}

	@Override
	public String getColumnName(int colIndex) {
		ColumnType colType = settings.getColumnAtIndex(colIndex);
		return colType.name();
	}
	
	private void updateLocations() {
		componentLocations = new HashMap<String,List<Integer>>();
		for (int row=0; row < getRowCount(); ++row) {
			AbstractComponent component = childrenList.get(row);
			if (component != null) {
				component.addViewManifestation(multiColViewManifestation);
				List<Integer> locations = componentLocations.get(getKey(component));
				if (locations == null) {
					locations = new ArrayList<Integer>();
					componentLocations.put(getKey(component), locations);
				}
				locations.add(row);
			}
		}
	}
	
	//TODO: include tableview's CTM's fireCellSettingsChanged?

	/**
	 * Returns a unique key for a given component. This key is used by the
	 * code that responds to a feed update to pass along changes to the
	 * component's value.
	 * 
	 * @param component the component for which to determine the key
	 * @return a unique key for the component
	 */
	public String getKey(AbstractComponent component) {
		AbstractComponent delegate = component;
		FeedProvider fp = component.getCapability(FeedProvider.class);
		if (fp != null) {
			return fp.getSubscriptionId();
		}
		
		return delegate.getComponentId();
	}
	
	public Object getValueAt(int r, int c) {
		ColumnType colType = settings.getColumnAtIndex(c);
		Object cellDatum = null;
		AbstractComponent cellComponent = childrenList.get(r);
		switch(colType) {
		case ID:
			cellDatum = ID_BASE + r; break;
		case TITLE:
			cellDatum = TITLE_BASE + r; break;
		case FSW_NAME: 
			cellDatum = FSW_BASE + r; break;
		case RAW:
			cellDatum = getValueForComponent(cellComponent); break;
		case VALUE:
			cellDatum = getValueForComponent(cellComponent); break;
		case UNIT:
			cellDatum = UNIT_BASE + r; break;
		case ERT:
			cellDatum = (FeedProvider) cellComponent; break;
		case SCLK:
			cellDatum = (FeedProvider) cellComponent; break;
		case SCET:
			cellDatum = (FeedProvider) cellComponent; break;
		}
		if(cellDatum==null) { return "(no data)"; }
		else                { return cellDatum; }
	}
	
	/**
	 * Sets the value of an object updated by a data feed. This change
	 * is propogated to all table cells displaying that object.
	 * 
	 * @param id the identifier for the object updated
	 * @param value the new value to display
	 */
	public void setValue(String id, Object value) {
		values.put(id, value);
		List<Integer> locations = componentLocations.get(id);
		if (locations != null) {
			for (Integer row : locations) {
				fireTableCellUpdated(row, settings.getIndexForColumn(ColumnType.VALUE)); 
				fireTableCellUpdated(row, settings.getIndexForColumn(ColumnType.RAW)); 
				fireTableCellUpdated(row, settings.getIndexForColumn(ColumnType.ERT)); 
				fireTableCellUpdated(row, settings.getIndexForColumn(ColumnType.SCET)); 
				fireTableCellUpdated(row, settings.getIndexForColumn(ColumnType.SCLK)); 
			}
		}
	}
	
	private Object getValueForComponent(AbstractComponent component) {
		Object value = values.get(getKey(component));
		//System.out.println("getValueForComponent ||||||||||||||||||||||||| " + values.size()); //debug
		if (value == null) {
			DisplayedValue displayedValue = new DisplayedValue();
			if (component.getCapability(Placeholder.class) != null) {
			    displayedValue.setValue(component.getCapability(Placeholder.class).getPlaceholderValue());
			} else {
				displayedValue.setValue(component.getDisplayName());
			}
			return displayedValue;
		} else {
			return value;
		}
	}
	
	public Class getColumnClass(int c) { return getValueAt(0, c).getClass(); }
	
	//copied somewhat blindly from labeledtablemodel.java:
	/**
	 * Adds a listener for events fired when the labels have been updated.
	 * 
	 * @param listener the label change listener.
	 */
	public void addLabelChangeListener(LabelChangeListener listener) {
		listenerManager.addListener(LabelChangeListener.class, listener);
	}
	
	/**
	 * Removes the listener for events.
	 * @param listener the label change listener.
	 */
	public void removeLabelChangeListener(LabelChangeListener listener) {
		listenerManager.removeListener(LabelChangeListener.class, listener);
	}

	protected Object getStoredObjectAt(int objectIndex, int attributeIndex) {
		return getValueAt(objectIndex, attributeIndex);
	}
	
	/**
	 * Handles an event where the row, column, or cell labels have changed.
	 * Triggers a structure changed event so that the table will be redrawn
	 * completely.
	 */
	public void fireLabelsChanged() {
		listenerManager.fireEvent(LabelChangeListener.class, new ListenerNotifier<LabelChangeListener>() {
			@Override
			public void notifyEvent(LabelChangeListener listener) {
				listener.labelsChanged();
			}
		});
	}

	@Override
	public void fireTableCellUpdated(int row, int column) {
		super.fireTableCellUpdated(row, column);
	}
	
	//TODO: code for changing this model when components are moved around in MCT component tree, etc
}