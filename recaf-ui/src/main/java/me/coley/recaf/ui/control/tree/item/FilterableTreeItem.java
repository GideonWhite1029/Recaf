package me.coley.recaf.ui.control.tree.item;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Predicate;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.scene.control.TreeItem;
import me.coley.recaf.util.logging.Logging;
import org.slf4j.Logger;

/**
 * Filterable tree item.
 *
 * @param <T>
 * 		Type of value held by the item.
 *
 * @author kaznovac - https://stackoverflow.com/a/34426897
 * @author Matt Coley - Optimization of child management.
 */
public class FilterableTreeItem<T> extends TreeItem<T> {
	private static final Logger logger = Logging.get(FilterableTreeItem.class);
	private final ObservableList<TreeItem<T>> sourceChildren = FXCollections.observableArrayList();
	private final ObjectProperty<Predicate<TreeItem<T>>> predicate = new SimpleObjectProperty<>();

	protected FilterableTreeItem() {
		// Support filtering by using a filtered list backing.
		// - Apply predicate to items
		FilteredList<TreeItem<T>> filteredChildren = new FilteredList<>(sourceChildren);
		filteredChildren.predicateProperty().bind(Bindings.createObjectBinding(() -> child -> {
			if (child instanceof FilterableTreeItem)
				((FilterableTreeItem<T>) child).predicateProperty().set(predicate.get());
			if (predicate.get() == null || !child.getChildren().isEmpty())
				return true;
			boolean matched = predicate.get().test(child);
			onMatchResult(child, matched);
			return matched;
		}, predicate));
		// Reflection hackery
		setUnderlyingChildren(filteredChildren);
	}

	/**
	 * Allow additional action on items when the {@link #predicate} is udpated.
	 *
	 * @param child
	 * 		Child item.
	 * @param matched
	 * 		Match status.
	 */
	protected void onMatchResult(TreeItem<T> child, boolean matched) {
		// no-op by default
	}

	/**
	 * Use reflection to directly set the underlying {@link TreeItem#children} field.
	 * The javadoc for that field literally says:
	 * <pre>
	 *  It is important that interactions with this list go directly into the
	 *  children property, rather than via getChildren(), as this may be
	 *  a very expensive call.
	 * </pre>
	 * This will additionally add the current tree-item's {@link TreeItem#childrenListener} to
	 * the given list's listeners.
	 *
	 * @param list
	 * 		Children list.
	 */
	@SuppressWarnings("unchecked")
	private void setUnderlyingChildren(ObservableList<TreeItem<T>> list) {
		try {
			// Add our change listener to the passed list.
			Field childrenListener = TreeItem.class.getDeclaredField("childrenListener");
			childrenListener.setAccessible(true);
			list.addListener((ListChangeListener<? super TreeItem<T>>) childrenListener.get(this));
			// Directly set "TreeItem.children"
			Field children = TreeItem.class.getDeclaredField("children");
			children.setAccessible(true);
			children.set(this, list);
		} catch (ReflectiveOperationException ex) {
			logger.error("Failed to update filterable children", ex);
		}
	}

	/**
	 * Add an unfiltered child to this item.
	 *
	 * @param item
	 * 		Child item to add.
	 */
	public void addSourceChild(TreeItem<T> item) {
		int index = Arrays.binarySearch(getChildren().toArray(), item);
		if (index < 0)
			index = -(index + 1);
		sourceChildren.add(index, item);
	}

	/**
	 * Remove an unfiltered child from this item.
	 *
	 * @param child
	 * 		Child item to remove.
	 */
	public void removeSourceChild(TreeItem<T> child) {
		sourceChildren.remove(child);
	}

	/**
	 * @return Predicate property.
	 */
	public ObjectProperty<Predicate<TreeItem<T>>> predicateProperty() {
		return predicate;
	}
}