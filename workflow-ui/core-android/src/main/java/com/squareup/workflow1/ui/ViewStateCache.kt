package com.squareup.workflow1.ui

import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
import android.util.SparseArray
import android.view.View
import android.view.View.BaseSavedState
import com.squareup.workflow1.ui.ViewStateCache.SavedState

/**
 * Handles persistence chores for container views that manage a set of [NamedViewRendering]s,
 * showing a view for one at a time -- think back stacks or tab sets.
 *
 * This class implements [Parcelable] so that it can be preserved from
 * a container view's own [View.saveHierarchyState] method. A simple container can
 * return [SavedState] from that method rather than creating its own persistence class.
 */
@WorkflowUiExperimentalApi
class ViewStateCache private constructor(
  private val viewStates: MutableMap<String, ViewStateFrame>
) : Parcelable {
  constructor() : this(mutableMapOf())

  /**
   * To be called when the set of hidden views changes but the visible view remains
   * the same. Any cached view state held for renderings that are not
   * [compatible][com.squareup.workflow1.ui.compatible] those in [retaining] will be dropped.
   */
  fun prune(retaining: Collection<NamedViewRendering>) {
    pruneKeys(retaining.map { it.compatibilityKey })
  }

  private fun pruneKeys(retaining: Collection<String>) {
    val deadKeys = viewStates.keys - retaining
    viewStates -= deadKeys
  }

  /**
   * @param retainedRenderings the renderings to be considered hidden after this update. Any
   * associated view state will be retained in the cache, possibly to be restored to [newView]
   * on a succeeding call to his method. Any other cached view state will be dropped.
   *
   * @param oldViewMaybe the view that is being removed, if any, which is expected to be showing
   * a [NamedViewRendering]. If that rendering is
   * [compatible with][com.squareup.workflow1.ui.compatible] a member of
   * [retainedRenderings], its state will be [saved][View.saveHierarchyState].
   *
   * @param newView the view that is about to be displayed, which must be showing a
   * [NamedViewRendering]. If [compatible][com.squareup.workflow1.ui.compatible]
   * view state is found in the cache, it is [restored][View.restoreHierarchyState].
   *
   * @return true if [newView] has been restored.
   */
  fun update(
    retainedRenderings: Collection<NamedViewRendering>,
    oldViewMaybe: View?,
    newView: View
  ) {
    val newKey = newView.namedKey
    val hiddenKeys = retainedRenderings.asSequence()
        .map { it.compatibilityKey }
        .toSet()
        .apply {
          require(retainedRenderings.size == size) {
            "Duplicate entries not allowed in $retainedRenderings."
          }
        }

    viewStates.remove(newKey)
        ?.let { newView.restoreHierarchyState(it.viewState) }

    if (oldViewMaybe != null) {
      oldViewMaybe.namedKey.takeIf { hiddenKeys.contains(it) }
          ?.let { savedKey ->
            val saved = SparseArray<Parcelable>().apply { oldViewMaybe.saveHierarchyState(this) }
            viewStates += savedKey to ViewStateFrame(savedKey, saved)
          }
    }

    pruneKeys(hiddenKeys)
  }

  /**
   * Replaces the state of the receiver with that of [from]. Typical usage is to call this from
   * a container view's [View.onRestoreInstanceState].
   */
  fun restore(from: ViewStateCache) {
    viewStates.clear()
    viewStates += from.viewStates
  }

  /**
   * Convenience for use in [View.onSaveInstanceState] and [View.onRestoreInstanceState]
   * methods of container views that have no other state of their own to save.
   *
   * More interesting containers should create their own subclass of [BaseSavedState]
   * rather than trying to extend this one.
   */
  class SavedState : BaseSavedState {
    constructor(
      superState: Parcelable?,
      viewStateCache: ViewStateCache
    ) : super(superState) {
      this.viewStateCache = viewStateCache
    }

    constructor(source: Parcel) : super(source) {
      this.viewStateCache = source.readParcelable(SavedState::class.java.classLoader)!!
    }

    val viewStateCache: ViewStateCache

    override fun writeToParcel(
      out: Parcel,
      flags: Int
    ) {
      super.writeToParcel(out, flags)
      out.writeParcelable(viewStateCache, flags)
    }

    companion object CREATOR : Creator<SavedState> {
      override fun createFromParcel(source: Parcel) =
        SavedState(source)

      override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
    }
  }

// region Parcelable

  override fun describeContents(): Int = 0

  override fun writeToParcel(
    parcel: Parcel,
    flags: Int
  ) {
    parcel.writeMap(viewStates as Map<*, *>)
  }

  companion object CREATOR : Creator<ViewStateCache> {
    override fun createFromParcel(parcel: Parcel): ViewStateCache {
      return mutableMapOf<String, ViewStateFrame>()
          .apply { parcel.readMap(this as Map<*, *>, ViewStateCache::class.java.classLoader) }
          .let { ViewStateCache(it) }
    }

    override fun newArray(size: Int): Array<ViewStateCache?> = arrayOfNulls(size)
  }

// endregion
}

@WorkflowUiExperimentalApi
private val View.namedKey: String
  get() {
    val rendering = getRendering<NamedViewRendering>()
    return checkNotNull(rendering?.compatibilityKey) {
      "Expected $this to be showing a ${NamedViewRendering::class.java.simpleName}<*> rendering, " +
          "found $rendering"
    }
  }