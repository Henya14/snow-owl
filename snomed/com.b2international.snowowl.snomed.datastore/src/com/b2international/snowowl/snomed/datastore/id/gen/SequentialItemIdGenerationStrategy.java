/*
 * Copyright 2017 B2i Healthcare Pte Ltd, http://b2i.sg
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.b2international.snowowl.snomed.datastore.id.gen;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;

import com.b2international.commons.CompareUtils;
import com.b2international.commons.Pair;
import com.b2international.snowowl.core.terminology.ComponentCategory;
import com.b2international.snowowl.datastore.store.Store;
import com.b2international.snowowl.datastore.store.query.Query;
import com.b2international.snowowl.datastore.store.query.QueryBuilder;
import com.b2international.snowowl.snomed.datastore.id.SnomedIdentifiers;
import com.b2international.snowowl.snomed.datastore.id.cis.SctId;
import com.b2international.snowowl.snomed.datastore.id.reservations.ISnomedIdentiferReservationService;
import com.b2international.snowowl.snomed.datastore.internal.id.reservations.ReservationRangeImpl;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.BoundType;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableRangeSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;

/**
 * An item identifier generation strategy that assigns item identifiers to components in a sequential
 * fashion.
 * 
 * @since 5.4
 */
public class SequentialItemIdGenerationStrategy implements ItemIdGenerationStrategy {

	private class ItemIdCounter {

		private final Range<Long> allowedRange;
		private final RangeSet<Long> excludedRanges;
		private final AtomicLong counter;
		
		public ItemIdCounter(final String namespace, final ComponentCategory category) {
			this.allowedRange = Range.closed(getLowerInclusiveId(namespace), getUpperExclusiveId(namespace));
			
			final SctId lastSctId = getLastSctId(namespace, category);
			if (lastSctId != null) {
				this.counter = new AtomicLong(lastSctId.getSequence());
			} else {
				// getNextItemId() will add 1 to this value immediately
				this.counter = new AtomicLong(allowedRange.lowerEndpoint() - 1L);
			}
			
			final FluentIterable<Range<Long>> excludedRangesIterable = FluentIterable.from(reservationService.getReservations())
				.filter(ReservationRangeImpl.class)
				.filter(new Predicate<ReservationRangeImpl>() {
					@Override public boolean apply(ReservationRangeImpl input) {
						return input.affects(namespace, category);
					}
				})
				.transform(new Function<ReservationRangeImpl, Range<Long>>() {
					@Override public Range<Long> apply(ReservationRangeImpl input) {
						return input.getItemIdRange();
					}
				});

			final ImmutableRangeSet.Builder<Long> excludedRangesBuilder = ImmutableRangeSet.builder();
			for (final Range<Long> range : excludedRangesIterable) {
				excludedRangesBuilder.add(range);
			}
			this.excludedRanges = excludedRangesBuilder.build();

			for (final Range<Long> excludedRange : excludedRanges.asRanges()) {
				if (!excludedRange.hasLowerBound() || !excludedRange.hasUpperBound()) {
					throw new IllegalStateException("All excluded ranges should have an lower and upper bound; found: " + excludedRange + ".");
				}
				
				if (!BoundType.CLOSED.equals(excludedRange.lowerBoundType()) || !BoundType.CLOSED.equals(excludedRange.upperBoundType())) {
					throw new IllegalStateException("All excluded ranges should have a closed lower and upper bound; found: " + excludedRange + ".");
				}
			}
		}

		private SctId getLastSctId(final String namespace, final ComponentCategory category) {
			final boolean intNamespace = CompareUtils.isEmpty(namespace);

			final Query query = QueryBuilder.newQuery()
				.match(SctId.Fields.NAMESPACE, intNamespace ? SnomedIdentifiers.INT_NAMESPACE : namespace)
				.match(SctId.Fields.PARTITION_ID, (intNamespace ? "0" : "1") + Integer.toString(category.ordinal()))
				.sortBy(SctId.Fields.SEQUENCE, true, false)
				.build();
			
			final Collection<SctId> hits = store.search(query, 0, 1);
			return Iterables.getOnlyElement(hits, null);
		}
		
		public long getNextItemId() {
			long current, next;
			
	        do {
	        	
	            current = counter.get();
				next = snapToLowerBound(current + 1L);
				
				Range<Long> firstRange = null;
				while (excludedRanges.contains(next)) {
				
					final Range<Long> container = excludedRanges.rangeContaining(next);
					if (firstRange == null) {
						firstRange = container;
					} else if (firstRange.equals(container)) {
						throw new IllegalStateException("Range visited twice while generating next item identifier: " + container);
					}
					
					// Step over this exclusion range and try again
					next = snapToLowerBound(container.upperEndpoint() + 1L);
					
					// "current" should also be skipped, so we eventually try to visit the first exclusion range seen, and throw an exception above
					if (next == current) {
						next = snapToLowerBound(next + 1L);
					}
				}

	        } while (!counter.compareAndSet(current, next));
	        
	        return next;
		}
		
		private long snapToLowerBound(final long value) {
			return allowedRange.contains(value) ? value : allowedRange.lowerEndpoint();
		}
	}
	
	private static final long MIN_INT_ITEMID = 100L;
	private static final long MAX_INT_ITEMID = 9999_9999_9999_999L; // 8 + 7 = 15 digits for itemId
	
	private static final long MIN_NAMESPACE_ITEMID = 1L;
	private static final long MAX_NAMESPACE_ITEMID = 9999_9999L; // 8 digits for itemId, 7 digits for namespaceId

	private static long getLowerInclusiveId(final String namespace) {
		return CompareUtils.isEmpty(namespace) ? MIN_INT_ITEMID : MIN_NAMESPACE_ITEMID;
	}
	
	private static long getUpperExclusiveId(final String namespace) {
		return CompareUtils.isEmpty(namespace) ? MAX_INT_ITEMID : MAX_NAMESPACE_ITEMID;
	}
	
	private final Store<SctId> store;
	private final ISnomedIdentiferReservationService reservationService;
	private final LoadingCache<Pair<String, ComponentCategory>, ItemIdCounter> lastItemIds;
	
	public SequentialItemIdGenerationStrategy(final Store<SctId> store, final ISnomedIdentiferReservationService reservationService) {
		this.store = store;
		this.reservationService = reservationService;
		this.lastItemIds = CacheBuilder.newBuilder().build(CacheLoader.from(new Function<Pair<String, ComponentCategory>, ItemIdCounter>() {
			@Override public ItemIdCounter apply(final Pair<String,ComponentCategory> input) {
				return new ItemIdCounter(input.getA(), input.getB());
			}
		}));
	}

	@Override
	public String generateItemId(final String namespace, final ComponentCategory category) {
		final Pair<String, ComponentCategory> key = Pair.identicalPairOf(Strings.emptyToNull(namespace), category);
		final long nextItemId = lastItemIds.getUnchecked(key).getNextItemId();
		return Long.toString(nextItemId);
	}
}
