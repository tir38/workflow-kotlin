@file:Suppress("FunctionName")

package com.squareup.workflow1.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import kotlin.reflect.KClass

/**
 * [ViewFactory]s that are always available.
 */
@WorkflowUiExperimentalApi
internal val defaultViewFactories = ViewRegistry(NamedViewFactory)

/**
 * The [ViewEnvironment] service that can be used to display the stream of renderings
 * from a workflow tree as [View] instances. This is the engine behind [AndroidViewRendering],
 * [WorkflowViewStub] and [ViewFactory]. Most apps can ignore [ViewRegistry] as an implementation
 * detail, by using [AndroidViewRendering] to tie their rendering classes to view code.
 *
 * To avoid that coupling between workflow code and the Android runtime, registries can
 * be loaded with [ViewFactory] instances at runtime, and provided as an optional parameter to
 * [WorkflowLayout.start].
 *
 * For example:
 *
 *     val AuthViewFactories = ViewRegistry(
 *       AuthorizingLayoutRunner, LoginLayoutRunner, SecondFactorLayoutRunner
 *     )
 *
 *     val TicTacToeViewFactories = ViewRegistry(
 *       NewGameLayoutRunner, GamePlayLayoutRunner, GameOverLayoutRunner
 *     )
 *
 *     val ApplicationViewFactories = ViewRegistry(ApplicationLayoutRunner) +
 *       AuthViewFactories + TicTacToeViewFactories
 *
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *       super.onCreate(savedInstanceState)
 *
 *       setContentWorkflow(
 *         registry = viewRegistry,
 *         configure = {
 *           WorkflowRunner.Config(rootWorkflow)
 *         }
 *       )
 *     }
 *
 * In the above example, it is assumed that the `companion object`s of the various
 * decoupled [LayoutRunner] classes honor a convention of implementing [ViewFactory], in
 * aid of this kind of assembly.
 *
 *     class GamePlayLayoutRunner(view: View) : LayoutRunner<GameRendering> {
 *
 *       // ...
 *
 *       companion object : ViewFactory<GameRendering> by LayoutRunner.bind(
 *         R.layout.game_layout, ::GameLayoutRunner
 *       )
 *     }
 */
@WorkflowUiExperimentalApi
public interface ViewRegistry {
  /**
   * The set of unique keys which this registry can derive from the renderings passed to [buildView]
   * and for which it knows how to create views.
   *
   * Used to ensure that duplicate bindings are never registered.
   */
  public val keys: Set<KClass<*>>

  /**
   * This method is not for general use, use [WorkflowViewStub] instead.
   *
   * Returns the [ViewFactory] that was registered for the given [renderingType], or null
   * if none was found.
   */
  public fun <RenderingT : Any> getFactoryFor(
    renderingType: KClass<out RenderingT>
  ): ViewFactory<RenderingT>?

  public companion object : ViewEnvironmentKey<ViewRegistry>(ViewRegistry::class) {
    override val default: ViewRegistry get() = ViewRegistry()
  }
}

@WorkflowUiExperimentalApi
public fun ViewRegistry(vararg bindings: ViewFactory<*>): ViewRegistry =
  TypedViewRegistry(*bindings)

/**
 * Returns a [ViewRegistry] that merges all the given [registries].
 */
@WorkflowUiExperimentalApi
public fun ViewRegistry(vararg registries: ViewRegistry): ViewRegistry =
  CompositeViewRegistry(*registries)

/**
 * Returns a [ViewRegistry] that contains no bindings.
 *
 * Exists as a separate overload from the other two functions to disambiguate between them.
 */
@WorkflowUiExperimentalApi
public fun ViewRegistry(): ViewRegistry = TypedViewRegistry()

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Creates a [View] to display [initialRendering], which can be updated via calls
 * to [View.showRendering]. Prefers entries found via [ViewRegistry.getFactoryFor].
 * If that returns null, falls back to the factory provided by the rendering's
 * implementation of [AndroidViewRendering.viewFactory], if there is one.
 *
 * The returned view will have a [WorkflowLifecycleOwner] set on it. The returned view msut EITHER:
 *
 * 1. Be attached at least once to ensure that the lifecycle eventually gets destroyed (because its
 *    parent is destroyed), or
 * 2. Have its [WorkflowLifecycleOwner.destroyOnDetach] called, which will either schedule the
 *    lifecycle to destroyed if the view is attached, or destroy it immediately if it's detached.
 *
 * @throws IllegalArgumentException if no factory can be find for type [RenderingT]
 *
 * @throws IllegalStateException if the matching [ViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  contextForNewView: Context,
  container: ViewGroup? = null
): View {
  @Suppress("UNCHECKED_CAST")
  val factory: ViewFactory<RenderingT> = getFactoryFor(initialRendering::class)
    ?: (initialRendering as? AndroidViewRendering<*>)?.viewFactory as? ViewFactory<RenderingT>
    ?: throw IllegalArgumentException(
      "A ${ViewFactory::class.qualifiedName} should have been registered to display " +
        "${initialRendering::class.qualifiedName} instances, or that class should implement " +
        "${AndroidViewRendering::class.simpleName}<${initialRendering::class.simpleName}>."
    )

  return factory.buildView(
    initialRendering,
    initialViewEnvironment,
    contextForNewView,
    container
  )
    .also { newView ->
      check(newView.getRendering<Any>() != null) {
        "View.bindShowRendering should have been called for $newView, typically by the " +
          "${ViewFactory::class.java.name} that created it."
      }

      WorkflowLifecycleOwner.installOn(newView)
    }
}

/**
 * It is usually more convenient to use [WorkflowViewStub] than to call this method directly.
 *
 * Creates a [View] to display [initialRendering], which can be updated via calls
 * to [View.showRendering].
 *
 * The returned view will have a [WorkflowLifecycleOwner] set on it, the caller _must_ ensure the
 * view gets attached to a window at least once, and call its
 * [WorkflowLifecycleOwner.destroyOnDetach] method before detaching the view for the final time
 * when replacing with another built view. Failing to do this can result in memory and other
 * resource leaks.
 *
 * @throws IllegalArgumentException if no binding can be find for type [RenderingT]
 *
 * @throws IllegalStateException if the matching [ViewFactory] fails to call
 * [View.bindShowRendering] when constructing the view
 */
@WorkflowUiExperimentalApi
public fun <RenderingT : Any> ViewRegistry.buildView(
  initialRendering: RenderingT,
  initialViewEnvironment: ViewEnvironment,
  container: ViewGroup
): View = buildView(initialRendering, initialViewEnvironment, container.context, container)

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(binding: ViewFactory<*>): ViewRegistry =
  this + ViewRegistry(binding)

@WorkflowUiExperimentalApi
public operator fun ViewRegistry.plus(other: ViewRegistry): ViewRegistry = ViewRegistry(this, other)
