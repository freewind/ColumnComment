package commenttools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import javax.persistence.Column;
import javax.persistence.Entity;

import org.apache.tools.ant.Task;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;
import org.jdom.xpath.XPath;

public class CommentToHbmTask extends Task {

	private String hbmDir;

	private String entityPkg;

	public void execute() {
		ClassLoader loader = this.getClass().getClassLoader();
		File dir = new File(loader.getResource(entityPkg.replace('.', '/')).getPath());
		System.out.println("Entity path: " + dir);
		for (File file : dir.listFiles()) {
			if (!file.getName().endsWith(".class")) {
				continue;
			}

			try {
				String classFullName = entityPkg + "." + file.getName().replace(".class", "");
				Class<?> cls = loader.loadClass(classFullName);
				Entity anno = cls.getAnnotation(Entity.class);
				if (anno != null) {
					// find corresponding .hbm.xml
					File hbm = new File(hbmDir, classFullName.replace(".", "/") + ".hbm.xml");
					if (!hbm.exists()) {
						System.out.println("File " + hbm + " is not exist, ignored");
						continue;
					}

					SAXBuilder builder = new SAXBuilder();
					// disable loading external dtd, otherwise it'll be extremely slow
					builder.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
					Document doc = builder.build(hbm);
					Element root = doc.getRootElement();

					// table comment
					HComment tableCommentAnno = cls.getAnnotation(HComment.class);
					if (tableCommentAnno != null) {
						Element comment = new Element("comment");
						comment.setText(tableCommentAnno.value());
						System.out.println("Adding table comment: " + comment);
						Element ele = (Element) XPath.newInstance("//class[@name='" + classFullName + "']").selectSingleNode(root);
						ele.getChildren().add(0, comment);
					}

					// column comments
					for (Field field : cls.getDeclaredFields()) {
						HComment commentAnno = field.getAnnotation(HComment.class);
						Column columnAnno = field.getAnnotation(Column.class);
						String columnName = field.getName();
						if (columnAnno != null && columnAnno.name() != null && columnAnno.name().trim().length() > 0) {
							columnName = columnAnno.name().trim();
						}
						System.out.println("Column name for " + field.getName() + " is: " + columnName);

						if (commentAnno != null) {
							//Element ele = doc.select("column[name=" + columnName + "]").first();
							Element ele = (Element) XPath.newInstance("//column[@name='" + columnName + "']").selectSingleNode(root);
							if (ele != null) {
								Element comment = new Element("comment");
								comment.setText(commentAnno.value());
								System.out.println("Add comment for field: " + comment);
								ele.getChildren().add(0, comment);
							}
						}
					}
					XMLOutputter outputter = new XMLOutputter();
					outputter.output(doc, new FileOutputStream(hbm));
				} else {
					System.out.println(cls.getName() + " is not annotated by Entity");
				}
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (JDOMException e) {
				e.printStackTrace();
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
		System.out.println("Adding comments completely");
	}

	public void setHbmDir(String hbmDir) {
		this.hbmDir = hbmDir;
	}

	public void setEntityPkg(String entityPkg) {
		this.entityPkg = entityPkg;
	}

}
