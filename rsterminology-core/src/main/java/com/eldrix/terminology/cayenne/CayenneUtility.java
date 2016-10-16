package com.eldrix.terminology.cayenne;

import java.util.List;
import java.util.function.Consumer;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.query.SelectQuery;

public class CayenneUtility {

	/**
	 * A very simple helper method to iterate through a select query showing progress.
	 * Most useful in a command line utility.
	 */
	public static <T> void timedBatchIterator(ObjectContext context, SelectQuery<T> query, int batchSize, long count, Consumer<List<T>> forEach) {
		long i = 1;
		long batches = count / batchSize;
		long estimated = 0;
		System.out.println("Processing " + count + ((batches == 0) ? "" : (" in " + batches + " batches...")));
		long start = System.currentTimeMillis();
		try (ResultBatchIterator<T> iterator = query.batchIterator(context, batchSize)) {
			for(List<T> batch : iterator) {
				System.out.print("\rProcessing batch " + i + "/" + batches + (estimated == 0 ? "" : " Remaining: ~" + estimated / 60000 + " min"));
				forEach.accept(batch);
				i++;
				long elapsed = System.currentTimeMillis() - start;
				estimated = (batches - i) * elapsed / i;
			}
		}
		System.out.println("\nFinished processing : " + i);
	}

}
