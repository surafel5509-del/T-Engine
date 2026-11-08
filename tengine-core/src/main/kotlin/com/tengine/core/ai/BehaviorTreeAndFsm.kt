package com.tengine.core.ai

/**
 * Strongly-typed Finite State Machine (FSM) for character controllers, bosses, and game states.
 */
interface FSMState<Owner> {
    fun onEnter(owner: Owner) {}
    fun onUpdate(owner: Owner, dt: Float) {}
    fun onExit(owner: Owner) {}
}

class StateMachine<Owner, StateId : Any>(
    private val owner: Owner
) {
    private val states = LinkedHashMap<StateId, FSMState<Owner>>()

    var currentStateId: StateId? = null
        private set

    var timeInCurrentState: Float = 0f
        private set

    fun addState(id: StateId, state: FSMState<Owner>): StateMachine<Owner, StateId> {
        states[id] = state
        return this
    }

    fun changeState(newStateId: StateId) {
        if (currentStateId == newStateId) return
        val nextState = states[newStateId] ?: return
        currentStateId?.let { states[it]?.onExit(owner) }
        currentStateId = newStateId
        timeInCurrentState = 0f
        nextState.onEnter(owner)
    }

    fun update(dt: Float) {
        val id = currentStateId ?: return
        timeInCurrentState += dt
        states[id]?.onUpdate(owner, dt)
    }
}

/**
 * Composable Behavior Tree AI Framework with Blackboard memory, Selector, Sequence,
 * Inverter, Cooldown, Condition, and Action nodes + Kotlin Builder DSL!
 */
enum class NodeStatus {
    SUCCESS,
    FAILURE,
    RUNNING
}

class Blackboard {
    private val data = HashMap<String, Any>()

    operator fun set(key: String, value: Any) {
        data[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = data[key] as? T

    fun remove(key: String) = data.remove(key)
    fun clear() = data.clear()
}

fun interface BTNode {
    fun tick(blackboard: Blackboard, dt: Float): NodeStatus
}

class SelectorNode(private val children: List<BTNode>) : BTNode {
    override fun tick(blackboard: Blackboard, dt: Float): NodeStatus {
        for (child in children) {
            when (val status = child.tick(blackboard, dt)) {
                NodeStatus.SUCCESS, NodeStatus.RUNNING -> return status
                NodeStatus.FAILURE -> continue
            }
        }
        return NodeStatus.FAILURE
    }
}

class SequenceNode(private val children: List<BTNode>) : BTNode {
    override fun tick(blackboard: Blackboard, dt: Float): NodeStatus {
        for (child in children) {
            when (val status = child.tick(blackboard, dt)) {
                NodeStatus.FAILURE, NodeStatus.RUNNING -> return status
                NodeStatus.SUCCESS -> continue
            }
        }
        return NodeStatus.SUCCESS
    }
}

class InverterNode(private val child: BTNode) : BTNode {
    override fun tick(blackboard: Blackboard, dt: Float): NodeStatus = when (child.tick(blackboard, dt)) {
        NodeStatus.SUCCESS -> NodeStatus.FAILURE
        NodeStatus.FAILURE -> NodeStatus.SUCCESS
        NodeStatus.RUNNING -> NodeStatus.RUNNING
    }
}

class CooldownNode(private val cooldownSeconds: Float, private val child: BTNode) : BTNode {
    private var remaining: Float = 0f

    override fun tick(blackboard: Blackboard, dt: Float): NodeStatus {
        if (remaining > 0f) {
            remaining -= dt
            return NodeStatus.FAILURE
        }
        val status = child.tick(blackboard, dt)
        if (status == NodeStatus.SUCCESS) {
            remaining = cooldownSeconds
        }
        return status
    }
}

class BehaviorTree(
    val root: BTNode,
    val blackboard: Blackboard = Blackboard()
) {
    fun tick(dt: Float): NodeStatus = root.tick(blackboard, dt)

    companion object {
        fun selector(vararg nodes: BTNode): BTNode = SelectorNode(nodes.toList())
        fun sequence(vararg nodes: BTNode): BTNode = SequenceNode(nodes.toList())
        fun invert(node: BTNode): BTNode = InverterNode(node)
        fun condition(predicate: (Blackboard) -> Boolean): BTNode = BTNode { bb, _ ->
            if (predicate(bb)) NodeStatus.SUCCESS else NodeStatus.FAILURE
        }
        fun action(block: (Blackboard, Float) -> NodeStatus): BTNode = BTNode(block)
    }
}
