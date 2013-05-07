package reshapes.command
import reshapes.DrawingSpaceState
import reshapes.figures.Shape
import java.awt.Point
import reshapes.Drawing
import reshapes.figures.Line
import reshapes.Reshapes
import java.util.UUID

@serializable
abstract class Command() {

  def execute() = {
    onExecute()
    Reshapes.CurrentEvents.getValue.Commands() = this :: Reshapes.CurrentEvents.getValue.Commands.getValue
  }

  def revert(): Unit = {
    // check if this command is latest command
    while (Reshapes.CurrentEvents.getValue.Commands.getValue.head != this) {
      // if not then revert all commands which where executed after this
      Reshapes.CurrentEvents.getValue.Commands.getValue.head.revert()
    }
    onRevert()
    Reshapes.CurrentEvents.getValue.Commands() = Reshapes.CurrentEvents.getValue.Commands.getValue.tail
  }

  def onExecute()
  def onRevert()
  def getCommandDescription(): String = {
    "Abstract command"
  }
}

/**
 * Deletes a given shape
 */
class DeleteShape(shapeToDelete: Shape) extends Command {

  def onExecute() = {
    Reshapes.CurrentEvents.getValue.allShapes() = Reshapes.CurrentEvents.getValue.allShapes.getValue filter (x => x != shapeToDelete)
  }

  def onRevert() = {
    Reshapes.CurrentEvents.getValue.allShapes() = shapeToDelete :: Reshapes.CurrentEvents.getValue.allShapes.getValue
  }

  override def getCommandDescription(): String = {
    "Delete %s".format(shapeToDelete)
  }
}

/**
 * Creates a new shape.
 */
class CreateShape(shapeToCreate: Shape) extends Command {

  def onExecute() {
    Reshapes.CurrentEvents.getValue.allShapes() = shapeToCreate :: Reshapes.CurrentEvents.getValue.allShapes.getValue
  }

  def onRevert() {
    var deleteCmd = new DeleteShape(shapeToCreate)
    deleteCmd.onExecute()
  }

  override def getCommandDescription(): String = {
    "Create %s".format(shapeToCreate)
  }
}

/**
 * Only implements onRevert() which restores a shape to state before resize, move, ...
 */
class EditShape(shapeBeforeEdit: Shape, shapeAfterEdit: Shape) extends Command {

  def onExecute() {

  }

  def onRevert() {
    shapeAfterEdit.path = shapeBeforeEdit.path
    shapeAfterEdit.strokeWidth = shapeBeforeEdit.strokeWidth
    shapeAfterEdit.color = shapeBeforeEdit.color

    Reshapes.CurrentEvents.getValue.selectedShape() = new Line() // force to fire selectedShape.changed event when Events.selectedShape() == shapeAfterEdit
    Reshapes.CurrentEvents.getValue.selectedShape() = shapeAfterEdit
  }

  override def getCommandDescription(): String = {
    "Edit %s".format(shapeAfterEdit)
  }
}

/**
 * Adds all shapes of given Events with currently selected Events.
 */
class MergeEvents(eventToMerge: DrawingSpaceState) extends Command {

  var eventTitle: String = null
  var shapes: List[Shape] = null

  def onExecute() {
    eventTitle = eventToMerge.fileName.getValue
    shapes = eventToMerge.allShapes.getValue

    shapes map (shape => new CreateShape(shape).onExecute())
  }

  def onRevert() {
    shapes map (shape => new DeleteShape(shape).onExecute())
  }

  override def getCommandDescription(): String = {
    "Merge with %s".format(eventTitle)
  }
}

