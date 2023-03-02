package helloworld;

import org.junit.Test;
import software.amazon.lambda.powertools.cloudformation.Response;

import static org.junit.Assert.assertEquals;

public class AppTest {
  @Test
  public void successfulCreate() {
    App app = new App();
    Response response = app.create(null, null);
    assertEquals(response.getPhysicalResourceId(), null);
    assertEquals(response.getStatus(), Response.Status.SUCCESS);
  }
  @Test
  public void successfulUpdate() {
    App app = new App();
    Response response = app.update(null, null);
    assertEquals(response.getPhysicalResourceId(), null);
    assertEquals(response.getStatus(), Response.Status.SUCCESS);
  }
  @Test
  public void successfulDelete() {
    App app = new App();
    Response response = app.delete(null, null);
    assertEquals(response.getPhysicalResourceId(), null);
    assertEquals(response.getStatus(), Response.Status.SUCCESS);
  }
}
