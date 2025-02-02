package com.dingyi.treeview

import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.dingyi.treeview.databinding.ActivityMainBinding
import com.dingyi.treeview.databinding.ItemDirBinding
import com.dingyi.treeview.databinding.ItemFileBinding
import io.github.dingyi222666.view.treeview.AbstractTree
import io.github.dingyi222666.view.treeview.Tree
import io.github.dingyi222666.view.treeview.TreeNode
import io.github.dingyi222666.view.treeview.TreeNodeGenerator
import io.github.dingyi222666.view.treeview.TreeNodeListener
import io.github.dingyi222666.view.treeview.TreeView
import io.github.dingyi222666.view.treeview.TreeViewBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)

        setContentView(binding.root)

        val tree = Tree.createTree<VirtualFile>()

        tree.generator = NodeGenerator()
        tree.initTree()

        binding.treeview.apply {
            supportHorizontalScroll = true
            bindCoroutineScope(lifecycleScope)
            this.tree = tree as Tree<Any>
            binder = ViewBinder() as TreeViewBinder<Any>
            nodeClickListener = binder
        }

        lifecycleScope.launch {
            binding.treeview.refresh()
        }

    }


    private fun repeatCreateVirtualFile(parent: VirtualFile, size: Int): VirtualFile {

        fun create(parentFile: VirtualFile): Pair<VirtualFile, VirtualFile> {
            val inside = VirtualFile("Test5", true)
            return VirtualFile("Test", true)
                .addChild(
                    VirtualFile("Test2", false),
                    VirtualFile("Test3", false),
                    VirtualFile("Test4", false),
                    inside
                ).apply {
                    parentFile.addChild(this)
                } to inside
        }

        var (currentRootVirtualFile, currentInsideVirtualFile) = create(parent)


        IntRange(1, size).forEach { _ ->
            currentInsideVirtualFile = create(currentInsideVirtualFile).second
        }

        return currentRootVirtualFile
    }

    fun createVirtualFile(): VirtualFile {
        val root = VirtualFile("app", true)
        root.addChild(
            VirtualFile("src", true)
                .addChild(
                    VirtualFile("main", true)
                        .addChild(
                            VirtualFile("java", true)
                                .addChild(
                                    VirtualFile("com.dingyi.treeview", true)
                                        .addChild(VirtualFile("MainActivity.kt", false))
                                )
                        ),
                    VirtualFile("res", true)
                        .addChild(
                            VirtualFile("layout", true)
                                .addChild(
                                    VirtualFile("activity_main", false)
                                )
                        ),
                    VirtualFile("test", true).apply {
                        repeatCreateVirtualFile(this, 20)
                    }
                ),
        )
        return root
    }

    inner class ViewBinder : TreeViewBinder<VirtualFile>(), TreeNodeListener<VirtualFile> {

        override fun createView(parent: ViewGroup, viewType: Int): View {
            if (viewType == 1) {
                return ItemDirBinding.inflate(layoutInflater, parent, false).root
            } else {
                return ItemFileBinding.inflate(layoutInflater, parent, false).root
            }
        }

        override fun areContentsTheSame(
            oldItem: TreeNode<VirtualFile>,
            newItem: TreeNode<VirtualFile>
        ): Boolean {
            return oldItem == newItem && oldItem.extra?.name == newItem.extra?.name
        }

        override fun areItemsTheSame(
            oldItem: TreeNode<VirtualFile>,
            newItem: TreeNode<VirtualFile>
        ): Boolean {
            return oldItem.id == newItem.id
        }

        override fun getItemViewType(node: TreeNode<VirtualFile>): Int {
            if (node.extra?.isDir == true) {
                return 1
            }
            return 0
        }

        override fun bindView(
            holder: TreeView.ViewHolder,
            node: TreeNode<VirtualFile>,
            listener: TreeNodeListener<VirtualFile>
        ) {
            if (node.hasChild) {
                applyDir(holder, node)
            } else {
                applyFile(holder, node)
            }
            val itemView = if (getItemViewType(node) == 1)
                ItemDirBinding.bind(holder.currentItemView).space
            else ItemFileBinding.bind(holder.currentItemView).space
            itemView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                width = node.level * 10.dp
            }
            //itemView.updatePadding(top = 0,right = 0, bottom = 0, left = node.level * 10.dp)

        }

        private fun applyFile(holder: TreeView.ViewHolder, node: TreeNode<VirtualFile>) {
            val binding = ItemFileBinding.bind(holder.currentItemView)
            binding.tvName.text = node.name.toString()

        }

        private fun applyDir(holder: TreeView.ViewHolder, node: TreeNode<VirtualFile>) {
            val binding = ItemDirBinding.bind(holder.currentItemView)
            binding.tvName.text = node.name.toString()

            binding
                .ivArrow
                .animate()
                .rotation(if (node.expand) 90f else 0f)
                .setDuration(0)
                .start()
        }


        override fun onClick(node: TreeNode<VirtualFile>, holder: TreeView.ViewHolder) {
            if (node.hasChild) {
                applyDir(holder, node)
            } else {
                applyFile(holder, node)
            }
        }

        override fun onToggle(
            node: TreeNode<VirtualFile>,
            isExpand: Boolean,
            holder: TreeView.ViewHolder
        ) {
            if (isExpand) {
                applyDir(holder, node)
            }
        }
    }

    inner class NodeGenerator : TreeNodeGenerator<VirtualFile> {

        private val root = createVirtualFile()

        override suspend fun refreshNode(
            targetNode: TreeNode<VirtualFile>,
            oldNodeSet: Set<Int>,
            withChild: Boolean,
            tree: AbstractTree<VirtualFile>,
        ): List<TreeNode<VirtualFile>> = withContext(Dispatchers.IO) {
            delay(100)
            val oldNodes = tree.getNodes(oldNodeSet)

            val child = checkNotNull(targetNode.extra?.getChild()).toMutableList()

            val result = mutableListOf<TreeNode<VirtualFile>>()

            oldNodes.forEach { node ->
                val virtualFile = child.find { it.name == node.extra?.name }
                if (virtualFile != null) {
                    result.add(node)
                }
                child.remove(virtualFile)
            }

            if (child.isEmpty()) {
                return@withContext result
            }

            child.forEach {
                result.add(
                    TreeNode(
                        it, targetNode.level + 1, it.name,
                        tree.generateId(), it.isDir && it.getChild().isNotEmpty(), false
                    )
                )
            }

            result
        }


        override fun createRootNode(): TreeNode<VirtualFile> {
            return TreeNode(root, 0, root.name, 0, true, true)
        }


    }

}

class VirtualFile(
    val name: String,
    val isDir: Boolean
) {

    private lateinit var parent: VirtualFile

    private val child = mutableListOf<VirtualFile>()

    fun addChild(file: VirtualFile): VirtualFile {
        child.add(file)
        file.parent = this
        return this
    }

    fun addChild(vararg virtualFile: VirtualFile): VirtualFile {
        virtualFile.forEach { addChild(it) }
        return this
    }

    fun getChild(): List<VirtualFile> = child

}

inline val Int.dp: Int
    get() = (Resources.getSystem().displayMetrics.density * this + 0.5f).toInt()